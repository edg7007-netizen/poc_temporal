package com.poc.temporal.lending

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class LendingEngineApplication

fun main(args: Array<String>) {
    runApplication<LendingEngineApplication>(*args)
}
