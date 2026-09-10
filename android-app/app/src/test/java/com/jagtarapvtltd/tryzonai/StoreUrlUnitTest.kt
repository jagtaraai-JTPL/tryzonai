package com.jagtarapvtltd.tryzonai

import com.jagtarapvtltd.tryzonai.models.Product
import com.jagtarapvtltd.tryzonai.ui.screens.getStoreUrlForProduct
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StoreUrlUnitTest {

    @Test
    fun testCatalogItemDirectMyntraUrl() {
        val product = Product(
            id = "1",
            name = "Roadster Cotton T-Shirt",
            url = "https://www.myntra.com/tshirts/roadster/12345"
        )
        val myntraUrl = getStoreUrlForProduct(product, "myntra")
        assertTrue(myntraUrl.contains("myntra.com/tshirts/roadster/12345"))
        assertTrue(myntraUrl.contains("subid=tryzonai"))
    }

    @Test
    fun testCustomItemMyntraSearchUrl() {
        val product = Product(
            id = "2",
            name = "Navy Blue Oversized T-Shirt",
            url = null
        )
        val myntraUrl = getStoreUrlForProduct(product, "myntra")
        assertEquals("https://www.myntra.com/search?rawQuery=Navy+Blue+Oversized+T-Shirt&subid=tryzonai", myntraUrl)
    }

    @Test
    fun testCustomItemAmazonSearchUrl() {
        val product = Product(
            id = "3",
            name = "Navy Blue Oversized T-Shirt",
            url = null
        )
        val amazonUrl = getStoreUrlForProduct(product, "amazon")
        assertEquals("https://www.amazon.in/s?k=Navy+Blue+Oversized+T-Shirt&tag=tryzonai-21", amazonUrl)
    }
}
