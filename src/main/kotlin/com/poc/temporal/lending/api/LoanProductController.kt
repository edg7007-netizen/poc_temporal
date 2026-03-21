package com.poc.temporal.lending.api

import com.poc.temporal.lending.domain.LoanProduct
import com.poc.temporal.lending.service.LoanProductService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/products")
class LoanProductController(private val loanProductService: LoanProductService) {

    @GetMapping
    fun getAllProducts(): ResponseEntity<List<LoanProduct>> =
        ResponseEntity.ok(loanProductService.getAllActiveProducts())

    @GetMapping("/{productId}")
    fun getProduct(@PathVariable productId: Long): ResponseEntity<LoanProduct> =
        ResponseEntity.ok(loanProductService.getProduct(productId))

    @PostMapping
    fun createProduct(@RequestBody product: LoanProduct): ResponseEntity<LoanProduct> =
        ResponseEntity.status(HttpStatus.CREATED).body(loanProductService.createProduct(product))

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleNotFound(ex: IllegalArgumentException): ResponseEntity<Map<String, String>> =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(mapOf("error" to (ex.message ?: "Not found")))
}
