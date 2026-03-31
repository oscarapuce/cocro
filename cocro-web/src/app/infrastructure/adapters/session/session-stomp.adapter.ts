import { Injectable, signal } from '@angular/core';
import { Client, IMessage, StompSubscription } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import { SessionSocketPort } from '@application/ports/session/session-socket.port';
import { environment } from '@infrastructure/environment';
import {
  GridCheckedEvent,
  GridUpdatedEvent,
  ParticipantJoinedEvent,
  ParticipantLeftEvent,
  SessionEndedEvent,
  SessionEvent,
  SessionInterruptedEvent,
  SessionWelcomeEvent,
  SyncRequiredEvent,
} from '@domain/models/session-events.model';

export type SessionEventHandler = (event: SessionEvent) => void;

@Injectable({ providedIn: 'root' })
export class SessionStompAdapter implements SessionSocketPort {
  readonly connected = signal(false);

  private client: Client | null = null;
  private subscriptions: StompSubscription[] = [];
  private heartbeatInterval: ReturnType<typeof setInterval> | null = null;

  connect(token: string, shareCode: string, onEvent: SessionEventHandler): void {
    this.disconnect();

    this.client = new Client({
      webSocketFactory: () => new SockJS(environment.wsUrl),
      connectHeaders: {
        Authorization: `Bearer ${token}`,
        shareCode,
      },
      reconnectDelay: 5000,
      onConnect: () => {
        this.connected.set(true);
        this.subscribeWelcome(shareCode, onEvent);
        this.startHeartbeat(shareCode);
      },
      onDisconnect: () => this.connected.set(false),
      onStompError: (frame) => console.error('STOMP error', frame),
    });

    this.client.activate();
  }

  disconnect(): void {
    this.stopHeartbeat();
    this.subscriptions.forEach((s) => s.unsubscribe());
    this.subscriptions = [];
    this.client?.deactivate();
    this.client = null;
    this.connected.set(false);
  }

  sendGridUpdate(
    shareCode: string,
    payload: { posX: number; posY: number; commandType: string; letter?: string },
  ): void {
    if (!this.client?.connected) return;
    this.client.publish({
      destination: `/app/session/${shareCode}/grid`,
      body: JSON.stringify(payload),
    });
  }

  private subscribeWelcome(shareCode: string, onEvent: SessionEventHandler): void {
    if (!this.client) return;

    // Welcome (synchronous @SubscribeMapping)
    const welcomeSub = this.client.subscribe(
      `/app/session/${shareCode}/welcome`,
      (msg: IMessage) => {
        const event = JSON.parse(msg.body) as SessionWelcomeEvent;
        onEvent(event);
        // After welcome, subscribe to broadcast topic
        this.subscribeTopic(shareCode, onEvent);
      },
    );
    this.subscriptions.push(welcomeSub);
  }

  private subscribeTopic(shareCode: string, onEvent: SessionEventHandler): void {
    if (!this.client) return;

    const topicSub = this.client.subscribe(
      `/topic/session/${shareCode}`,
      (msg: IMessage) => {
        const event = JSON.parse(msg.body) as
          | ParticipantJoinedEvent
          | ParticipantLeftEvent
          | GridUpdatedEvent
          | GridCheckedEvent
          | SessionEndedEvent
          | SessionInterruptedEvent;
        onEvent(event);
      },
    );

    const privateSub = this.client.subscribe('/user/queue/session', (msg: IMessage) => {
      const event = JSON.parse(msg.body) as SyncRequiredEvent;
      onEvent(event);
    });

    this.subscriptions.push(topicSub, privateSub);
  }

  /** Send a heartbeat to the BFF every 20s to stay ACTIVE (grace period is 30s). */
  private startHeartbeat(shareCode: string): void {
    this.stopHeartbeat();
    this.heartbeatInterval = setInterval(() => {
      if (this.client?.connected) {
        this.client.publish({ destination: `/app/session/${shareCode}/heartbeat` });
      }
    }, 20_000);
  }

  private stopHeartbeat(): void {
    if (this.heartbeatInterval) {
      clearInterval(this.heartbeatInterval);
      this.heartbeatInterval = null;
    }
  }
}
