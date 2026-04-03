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

  it('shows the reference when provided', () => {
    create({ reference: 'GRD-001' });
    const ref = fixture.nativeElement.querySelector('.play-header__reference');
    expect(ref?.textContent).toContain('GRD-001');
  });

  it('does not show reference when not provided', () => {
    create({});
    const ref = fixture.nativeElement.querySelector('.play-header__reference');
    expect(ref).toBeNull();
  });

  it('shows the difficulty when not NONE', () => {
    create({ difficulty: '3' });
    const diff = fixture.nativeElement.querySelector('.play-header__difficulty');
    expect(diff).not.toBeNull();
    expect(diff?.textContent).toContain('3');
  });

  it('does not show difficulty when NONE', () => {
    create({ difficulty: 'NONE' });
    const diff = fixture.nativeElement.querySelector('.play-header__difficulty');
    expect(diff).toBeNull();
  });

  it('shows participant count with plural', () => {
    create({ participantCount: 3 });
    const el = fixture.nativeElement.querySelector('.play-header__players');
    expect(el?.textContent).toContain('3');
    expect(el?.textContent).toContain('joueurs');
  });

  it('shows singular joueur for 1 participant', () => {
    create({ participantCount: 1 });
    const el = fixture.nativeElement.querySelector('.play-header__players');
    expect(el?.textContent).toContain('1 joueur');
    expect(el?.textContent).not.toContain('joueurs');
  });

  it('shows online dot when connected', () => {
    create({ connected: true });
    const dot = fixture.nativeElement.querySelector('.play-header__dot--online');
    expect(dot).not.toBeNull();
  });

  it('does not show online dot when disconnected', () => {
    create({ connected: false });
    const dot = fixture.nativeElement.querySelector('.play-header__dot--online');
    expect(dot).toBeNull();
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
