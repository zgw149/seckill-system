package org.example.orderservice.client;

import org.example.orderservice.dto.*;
import org.example.orderservice.dto.ReserveStockRequest;
import org.example.productservice.dto.StockOperationResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "product-service")
public interface ProductClient {

    // 注入productRestClient
    // 从spring容器中寻找，此时只有一个RestClient，所以可以直接注入
    // 如果有多个可以加@Qualifier注解指定bean的名称
//    public ProductClient(RestClient productRestClient) {
//        this.productRestClient = productRestClient;
//    }
    // retrieve发起请求，前面是拼接url接口，后面是返回类型
    @GetMapping("/products/{id}")
    ProductDTO getProduct(@PathVariable("id") Long id);

    //库存预占
    @PostMapping("/products/{id}/reservations")
    StockOperationResponse reserveStock(
            @PathVariable("id") Long id,
            @RequestBody ReserveStockRequest request
    );
    // 查询某笔库存预占
    @GetMapping("/products/reservations/{reservationNo}")
    StockOperationResponse reservationStatus(
            @PathVariable("reservationNo") String reservationNo);
    // 释放某笔库存预占
    @PostMapping("/products/reservations/{reservationNo}/release")
    StockOperationResponse releaseStock(
            @PathVariable("reservationNo") String reservationNo);
    /**
     * 取消一笔库存预占。
     *
     * 与 releaseStock 不同：
     * 如果预占记录尚不存在，商品服务也会创建 CANCELLED 墓碑。
     */
    @PostMapping("/products/{id}/reservations/cancel")
    org.example.orderservice.dto.StockOperationResponse cancelReservation(
            @PathVariable("id") Long productId,
            @RequestBody ReserveStockRequest request
    );
}
