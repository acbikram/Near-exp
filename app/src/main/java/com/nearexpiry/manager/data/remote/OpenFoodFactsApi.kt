package com.nearexpiry.manager.data.remote

import com.nearexpiry.manager.domain.model.ProductInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Free, no-API-key barcode lookup via Open Food Facts
 * (https://world.openfoodfacts.org), used as an opt-in fallback when a
 * scanned barcode isn't found in the bundled `products.db` catalog or the
 * user's saved custom products.
 *
 * Returns null on any network error, timeout, "not found" response, or if
 * the product has no usable name in either language — callers should treat
 * null as "no result" and let the user enter details manually as before.
 */
object OpenFoodFactsApi {

    private const val TIMEOUT_MS = 8000
    private const val USER_AGENT = "NearExpiryManager/1.0 (Android)"

    suspend fun fetchProduct(barcode: String): ProductInfo? = withContext(Dispatchers.IO) {
        try {
            val url = URL(
                "https://world.openfoodfacts.org/api/v2/product/$barcode.json" +
                    "?fields=product_name,product_name_ar,product_name_en,quantity"
            )
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                setRequestProperty("User-Agent", USER_AGENT)
            }

            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                connection.disconnect()
                return@withContext null
            }

            val body = connection.inputStream.bufferedReader().use { it.readText() }
            connection.disconnect()

            val json = JSONObject(body)
            // status == 1 means the product was found; 0 means "not found".
            if (json.optInt("status", 0) != 1) return@withContext null

            val product = json.optJSONObject("product") ?: return@withContext null

            val nameEn = product.optString("product_name_en").takeIf { it.isNotBlank() }
                ?: product.optString("product_name").takeIf { it.isNotBlank() }
            val nameAr = product.optString("product_name_ar").takeIf { it.isNotBlank() }

            // If neither language has a usable name, there's nothing useful to show.
            if (nameEn == null && nameAr == null) return@withContext null

            // "quantity" is a free-text field like "500 g" or "1 L" — not a
            // clean unit code like our catalog's PCS/KGS/CTN/OFR, so we
            // intentionally leave `unit` null rather than guess.
            ProductInfo(
                barcode = barcode,
                name = nameEn,
                nameArabic = nameAr,
                unit = null,
                itemCode = null
            )
        } catch (e: Exception) {
            null
        }
    }
}
