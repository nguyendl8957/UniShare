package vn.unishare.feature.seller.ui

import vn.unishare.domain.repository.DraftMetadata

/**
 * Converts C04 Seller Wizard metadata into the
 * A04 backend contract used by Firebase.
 *
 * No Firebase calls are performed in this mapper.
 */
object A04MetadataMapper {

    fun toDraftMetadata(
        state: UploadWizardUiState
    ): DraftMetadata {
        val title = state.title.trim()
        val price = parsePriceVnd(state.priceText)

        require(title.length >= 5) {
            "Tiêu đề phải có ít nhất 5 ký tự."
        }

        require(
            price != null &&
                    price in MIN_SELLER_PRICE_VND..MAX_SELLER_PRICE_VND
        ) {
            "Giá bán không hợp lệ."
        }

        require(state.hasOwnershipConsent) {
            "Bạn phải xác nhận quyền đăng bán tài liệu."
        }

        return DraftMetadata(
            title = title,
            subject = mapSubject(state.subject),
            category = mapCategory(state.category),
            description = state.description.trim(),
            priceVnd = price.toLong()
        )
    }

    fun mapCategory(label: String): String {
        return when (label.trim()) {
            "Note tóm tắt" -> "note"
            "Cheat-sheet" -> "cheatsheet"
            "Mã nguồn" -> "source_code"
            "Đồ án" -> "project"
            "Khác" -> "other"
            else -> throw IllegalArgumentException(
                "Danh mục không được hỗ trợ: $label"
            )
        }
    }

    fun mapSubject(label: String): String {
        return when (label.trim()) {
            "Lập trình",
            "Công nghệ thông tin" -> "programming"

            "Cơ sở dữ liệu" -> "database"
            "Mạng máy tính" -> "network"
            "Hệ điều hành" -> "os"

            "Kinh tế vi mô",
            "Kinh tế học" -> "economics"

            "Toán cao cấp",
            "Khác" -> "other"

            else -> throw IllegalArgumentException(
                "Môn học không được hỗ trợ: $label"
            )
        }
    }
}