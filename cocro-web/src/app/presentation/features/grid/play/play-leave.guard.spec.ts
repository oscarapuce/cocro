import { playLeaveGuard } from './play-leave.guard';
import { GridPlayerComponent } from './grid-player.component';
import { signal } from '@angular/core';

describe('playLeaveGuard', () => {
  function createMockComponent(isConnected: boolean): GridPlayerComponent {
    return {
      connected: signal(isConnected),
      leave: jest.fn(),
    } as unknown as GridPlayerComponent;
  }

  it('should call leave() when component is connected', () => {
    const component = createMockComponent(true);
    const result = playLeaveGuard(component, {} as any, {} as any, {} as any);
    expect(component.leave).toHaveBeenCalled();
    expect(result).toBe(true);
  });

  it('should not call leave() when component is not connected', () => {
    const component = createMockComponent(false);
    const result = playLeaveGuard(component, {} as any, {} as any, {} as any);
    expect(component.leave).not.toHaveBeenCalled();
    expect(result).toBe(true);
  });

  it('should always return true (allow navigation)', () => {
    const component = createMockComponent(true);
    expect(playLeaveGuard(component, {} as any, {} as any, {} as any)).toBe(true);
  });
});

