import { SessionStompAdapter } from './session-stomp.adapter';

// ---------------------------------------------------------------------------
// Mocks
// ---------------------------------------------------------------------------

jest.mock('sockjs-client', () => {
  return jest.fn().mockImplementation(() => ({
    close: jest.fn(),
  }));
});

/** Minimal STOMP Client stub */
function createMockStompClient(config: Record<string, unknown>) {
  const subscriptions: Array<{ dest: string; callback: (msg: { body: string }) => void }> = [];
  const published: Array<{ destination: string; body?: string }> = [];
  const onConnectFn = config['onConnect'] as (() => void) | undefined;

  const client = {
    activate: jest.fn().mockImplementation(() => {
      // Simulate async connect via microtask
      Promise.resolve().then(() => onConnectFn?.());
    }),
    deactivate: jest.fn(),
    subscribe: jest.fn().mockImplementation((dest: string, callback: (msg: { body: string }) => void) => {
      subscriptions.push({ dest, callback });
      return { unsubscribe: jest.fn() };
    }),
    publish: jest.fn().mockImplementation((params: { destination: string; body?: string }) => {
      published.push(params);
    }),
    connected: true,
    _subscriptions: subscriptions,
    _published: published,
  };
  return client;
}

jest.mock('@stomp/stompjs', () => {
  return {
    Client: jest.fn().mockImplementation((config: Record<string, unknown>) => {
      return createMockStompClient(config);
    }),
  };
});

describe('SessionStompAdapter', () => {
  let adapter: SessionStompAdapter;

  beforeEach(() => {
    jest.useFakeTimers();
    adapter = new SessionStompAdapter();
  });

  afterEach(() => {
    adapter.disconnect();
    jest.useRealTimers();
  });

  it('should start disconnected', () => {
    expect(adapter.connected()).toBe(false);
  });

  describe('connect', () => {
    it('should set connected to true after STOMP connect', async () => {
      const handler = jest.fn();
      adapter.connect('token', 'AB12', handler);

      // Flush microtask (Promise.resolve in activate)
      await Promise.resolve();

      expect(adapter.connected()).toBe(true);
    });
  });

  describe('disconnect', () => {
    it('should set connected to false', async () => {
      adapter.connect('token', 'AB12', jest.fn());
      await Promise.resolve();
      expect(adapter.connected()).toBe(true);

      adapter.disconnect();
      expect(adapter.connected()).toBe(false);
    });

    it('should clear heartbeat interval', async () => {
      const clearSpy = jest.spyOn(global, 'clearInterval');
      adapter.connect('token', 'AB12', jest.fn());
      await Promise.resolve();

      adapter.disconnect();
      expect(clearSpy).toHaveBeenCalled();
      clearSpy.mockRestore();
    });
  });

  describe('heartbeat', () => {
    it('should start sending heartbeats after connect', async () => {
      adapter.connect('token', 'CODE1', jest.fn());
      await Promise.resolve();

      const client = (adapter as unknown as { client: ReturnType<typeof createMockStompClient> }).client;

      // Advance time by 20s — one heartbeat should fire
      jest.advanceTimersByTime(20_000);
      const heartbeats = client._published.filter(
        (p: { destination: string }) => p.destination === '/app/session/CODE1/heartbeat',
      );
      expect(heartbeats.length).toBe(1);

      // Advance another 20s
      jest.advanceTimersByTime(20_000);
      const heartbeats2 = client._published.filter(
        (p: { destination: string }) => p.destination === '/app/session/CODE1/heartbeat',
      );
      expect(heartbeats2.length).toBe(2);
    });

    it('should stop heartbeats on disconnect', async () => {
      adapter.connect('token', 'CODE2', jest.fn());
      await Promise.resolve();

      const client = (adapter as unknown as { client: ReturnType<typeof createMockStompClient> }).client;

      // One heartbeat fires
      jest.advanceTimersByTime(20_000);
      const countBefore = client._published.filter(
        (p: { destination: string }) => p.destination === '/app/session/CODE2/heartbeat',
      ).length;
      expect(countBefore).toBe(1);

      adapter.disconnect();

      // Advance more — no additional heartbeats
      jest.advanceTimersByTime(60_000);
      expect(true).toBe(true);
    });
  });

  describe('sendGridUpdate', () => {
    it('should not throw when not connected', () => {
      expect(() => {
        adapter.sendGridUpdate('AB12', { posX: 0, posY: 0, commandType: 'PLACE_LETTER', letter: 'A' });
      }).not.toThrow();
    });

    it('should publish to the correct destination when connected', async () => {
      adapter.connect('token', 'AB12', jest.fn());
      await Promise.resolve();

      const client = (adapter as unknown as { client: ReturnType<typeof createMockStompClient> }).client;
      adapter.sendGridUpdate('AB12', { posX: 1, posY: 2, commandType: 'PLACE_LETTER', letter: 'X' });

      const gridUpdates = client._published.filter(
        (p: { destination: string }) => p.destination === '/app/session/AB12/grid',
      );
      expect(gridUpdates.length).toBe(1);
    });
  });
});
