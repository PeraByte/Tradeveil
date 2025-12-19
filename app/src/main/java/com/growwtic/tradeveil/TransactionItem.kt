package com.growwtic.tradeveil

sealed class TransactionItem {
    data class SentTransfer(val transfer: Transfer) : TransactionItem()
    data class ReceivedTransfer(val transfer: Transfer) : TransactionItem()
}