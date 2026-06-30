import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ChartModalComponent } from './chart-modal';

describe('ChartModal', () => {
  let component: ChartModalComponent;
  let fixture: ComponentFixture<ChartModalComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ChartModalComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(ChartModalComponent);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
