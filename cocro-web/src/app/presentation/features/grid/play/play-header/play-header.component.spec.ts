import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { PlayHeaderComponent } from './play-header.component';
import { ButtonComponent } from '@presentation/shared/components/button/button.component';

describe('PlayHeaderComponent', () => {
  let fixture: ComponentFixture<PlayHeaderComponent>;

  function create(inputs: Partial<{
    shareCode: string;
    title: string;
    author: string;
    difficulty: string;
    reference: string;
    participantCount: number;
    revision: number;
    connected: boolean;
  }> = {}): ComponentFixture<PlayHeaderComponent> {
    TestBed.configureTestingModule({
      imports: [PlayHeaderComponent, ButtonComponent],
    });
    fixture = TestBed.createComponent(PlayHeaderComponent);
    fixture.componentRef.setInput('shareCode', inputs.shareCode ?? 'AB12');
    fixture.componentRef.setInput('title', inputs.title ?? 'Ma Grille');
    if (inputs.author !== undefined) fixture.componentRef.setInput('author', inputs.author);
    if (inputs.difficulty !== undefined) fixture.componentRef.setInput('difficulty', inputs.difficulty);
    if (inputs.reference !== undefined) fixture.componentRef.setInput('reference', inputs.reference);
    if (inputs.participantCount !== undefined) fixture.componentRef.setInput('participantCount', inputs.participantCount);
    if (inputs.revision !== undefined) fixture.componentRef.setInput('revision', inputs.revision);
    if (inputs.connected !== undefined) fixture.componentRef.setInput('connected', inputs.connected);
    fixture.detectChanges();
    return fixture;
  }

  it('shows the grid title', () => {
    create({ title: 'Mots Croisés du Dimanche' });
    const title = fixture.nativeElement.querySelector('.play-header__title');
    expect(title?.textContent?.trim()).toBe('Mots Croisés du Dimanche');
  });

  it('shows the share code', () => {
    create({ shareCode: 'XY99' });
    const code = fixture.nativeElement.querySelector('.play-header__code');
    expect(code?.textContent?.trim()).toBe('XY99');
  });

  it('shows the author when provided', () => {
    create({ author: 'Oscar' });
    const author = fixture.nativeElement.querySelector('.play-header__author');
    expect(author?.textContent?.trim()).toContain('Oscar');
  });

  it('does not show author section when author is empty', () => {
    create({ author: '' });
    const author = fixture.nativeElement.querySelector('.play-header__author');
    expect(author).toBeNull();
  });

  it('shows the difficulty badge when difficulty is not NONE', () => {
    create({ difficulty: '3' });
    const badge = fixture.nativeElement.querySelector('.play-header__difficulty.badge');
    expect(badge).not.toBeNull();
    expect(badge?.textContent).toContain('3');
  });

  it('does not show difficulty badge when NONE', () => {
    create({ difficulty: 'NONE' });
    const badge = fixture.nativeElement.querySelector('.play-header__difficulty.badge');
    expect(badge).toBeNull();
  });

  it('shows the reference when provided', () => {
    create({ reference: 'GRD-001' });
    const ref = fixture.nativeElement.querySelector('.play-header__reference');
    expect(ref?.textContent).toContain('GRD-001');
  });

  it('shows participant count', () => {
    create({ participantCount: 3 });
    const info = fixture.nativeElement.querySelector('.play-header__session');
    expect(info?.textContent).toContain('3');
    expect(info?.textContent).toContain('joueurs');
  });

  it('shows singular joueur for 1 participant', () => {
    create({ participantCount: 1 });
    const info = fixture.nativeElement.querySelector('.play-header__session');
    expect(info?.textContent).toContain('1 joueur');
  });

  it('shows offline indicator when not connected', () => {
    create({ connected: false });
    const offline = fixture.nativeElement.querySelector('.play-header__offline');
    expect(offline).not.toBeNull();
  });

  it('does not show offline indicator when connected', () => {
    create({ connected: true });
    const offline = fixture.nativeElement.querySelector('.play-header__offline');
    expect(offline).toBeNull();
  });

  it('emits leave event when leave button clicked', () => {
    create({});
    const leaveSpy = jest.fn();
    fixture.componentInstance.leave.subscribe(leaveSpy);
    const btn = fixture.debugElement.query(By.directive(ButtonComponent));
    btn.triggerEventHandler('click');
    expect(leaveSpy).toHaveBeenCalledTimes(1);
  });
});
