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
  SessionEvent,
  SessionWelcomeEvent,
  SyncRequiredEvent,
} from '@domain/models/session-events.model';

export type SessionEventHandler = (event: SessionEvent) => void;

@Injectable({ providedIn: 'root' })
export class SessionStompAdapter implements SessionSocketPort {
  readonly connected = signal(false);

  private client: Client | null = null;
  private subscriptions: StompSubscription[] = [];

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
      },
      onDisconnect: () => this.connected.set(false),
      onStompError: (frame) => console.error('STOMP error', frame),
    });

    this.client.activate();
  }

  disconnect(): void {
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
          | GridCheckedEvent;
        onEvent(event);
      },
    );

    const privateSub = this.client.subscribe('/user/queue/session', (msg: IMessage) => {
      const event = JSON.parse(msg.body) as SyncRequiredEvent;
      onEvent(event);
    });

    this.subscriptions.push(topicSub, privateSub);
  }
}
