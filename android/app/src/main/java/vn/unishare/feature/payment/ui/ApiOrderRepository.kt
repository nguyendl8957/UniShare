package vn.unishare.feature.payment.ui

import vn.unishare.data.remote.OrderApiAdapter
import vn.unishare.data.remote.RemoteOrder

/**
 * Production adapter: B04 Checkout -> A07 authenticated backend API.
 *
 * This class never creates a PAID state locally.
 */
class ApiOrderRepository(
    private val api: OrderApiAdapter
) : OrderRepository {

    override suspend fun createOrGetOrder(
        docId: String,
        idempotencyKey: String
    ): Result<OrderUiModel> = runCatching {
        api.createOrGetOrder(docId, idempotencyKey).toUiModel()
    }

    override suspend fun getOrderStatus(
        orderId: String
    ): Result<OrderUiModel> = runCatching {
        api.getOrderStatus(orderId).toUiModel()
    }

    private fun RemoteOrder.toUiModel(): OrderUiModel {
        val serverStatus = OrderStatus.valueOf(status)

        val validTransfer = transferInstructions?.takeIf {
            serverStatus == OrderStatus.PENDING &&
                it.amountVnd == amountVnd &&
                it.paymentCode == paymentCode &&
                it.bankName.isNotBlank() &&
                it.bankCode.isNotBlank() &&
                it.accountNumber.isNotBlank() &&
                it.accountHolder.isNotBlank()
        }

        return OrderUiModel(
            orderId = orderId,
            docId = docId,
            documentTitle = documentTitle,
            amountVnd = amountVnd,
            status = serverStatus,
            paymentCode = paymentCode,
            createdAtUtc = createdAt,
            expiresAtUtc = expiresAt,
            transferInfo = validTransfer?.let {
                BankTransferInfo(
                    bankName = it.bankName,
                    bankCode = it.bankCode,
                    accountNumber = it.accountNumber,
                    accountHolder = it.accountHolder,
                    amountVnd = it.amountVnd,
                    paymentCode = it.paymentCode
                )
            }
        )
    }
}