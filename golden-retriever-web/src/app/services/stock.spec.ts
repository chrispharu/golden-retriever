import { TestBed } from '@angular/core/testing';
import { StockService } from './stock';
import { HttpClientTestingModule } from '@angular/common/http/testing';

describe('StockService', () => {
  let service: StockService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule]
    });
    service = TestBed.inject(StockService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });
});
