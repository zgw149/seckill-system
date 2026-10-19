package org.example.productservice.controller;

import org.example.productservice.dto.DeductStockRequest;
import org.example.productservice.dto.DeductStockResponse;
import org.example.productservice.dto.ReserveStockRequest;
import org.example.productservice.dto.StockOperationResponse;
import org.example.productservice.entity.Product;
import org.example.productservice.service.ProductService;
import org.example.productservice.service.StockReservationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/products")
public class ProductController {
    @Autowired
    private ProductService productService;

    @Autowired
    private StockReservationService stockReservationService;

    //预占库存
    @PostMapping("/{productId}/reservations")
    public StockOperationResponse reserve(@PathVariable("productId") Long productId,
                                          @RequestBody ReserveStockRequest request){
        return stockReservationService.reserve(productId,request);
    }

    // 查询预占状态
    @GetMapping("/reservations/{reservationNo}")
    public StockOperationResponse status(@PathVariable String reservationNo){
        return stockReservationService.status(reservationNo);
    }

    // 幂等释放库存
    @PostMapping("/reservations/{reservationNo}/release")
    public StockOperationResponse release(@PathVariable String reservationNo) {
        return stockReservationService.release(reservationNo);
    }
    // 取消订单库存
    @PostMapping("/{productId}/reservations/cancel")
    public StockOperationResponse cancel(
            @PathVariable("productId") Long productId,
            @RequestBody ReserveStockRequest request) {
        return stockReservationService.cancel(productId, request);
    }

    // 同一预占编号发生并发唯一键冲突
    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<Map<String, String>> handleDuplicate(
            DuplicateKeyException ex) {
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(Map.of(
                        "code", "DUPLICATE_RESERVATION",
                        "message", "预占编号正在处理中，请使用原编号稍后重试"
                ));
    }

    @GetMapping("/{id}")
    public Product getProduct(@PathVariable("id") Long id) {
        return productService.getProduct(id);
    }

    @PostMapping("/{id}/deduct")
    public DeductStockResponse deductStock(
            @PathVariable("id") Long id,
            @RequestBody DeductStockRequest request) {
        boolean success = productService.deductStock(id, request.quantity());
        if (success) {
            return new DeductStockResponse(true, "扣库存成功");
        }
        return new DeductStockResponse(false, "库存不足");
    }
}
