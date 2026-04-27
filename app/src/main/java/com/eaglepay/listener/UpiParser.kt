package com.eaglepay.listener

import java.util.regex.Pattern

/**
 * Extracts amount + UTR from UPI/bank notification text.
 * Handles formats from GPay, PhonePe, Paytm, BHIM, SBI, HDFC, ICICI, Axis,
 * Yes, Kotak, Federal, IDFC, IndusInd, RBL, AU, Bank of Baroda etc.
 */
object UpiParser {

    data class Parsed(val amount: Double, val utr: String?, val payerVpa: String?)

    // ₹500 / Rs. 500 / Rs.500 / INR 500 / 500.00
    private val AMOUNT_PATTERNS = listOf(
        Pattern.compile("(?:credited|received|deposited|added).*?(?:rs\\.?|inr|₹)\\s*([0-9,]+(?:\\.[0-9]{1,2})?)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(?:rs\\.?|inr|₹)\\s*([0-9,]+(?:\\.[0-9]{1,2})?).*?(?:credited|received|deposited|added)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(?:rs\\.?|inr|₹)\\s*([0-9,]+(?:\\.[0-9]{1,2})?)", Pattern.CASE_INSENSITIVE),
    )

    // UTR / RRN / Ref No. — typically 12 digits, sometimes alphanumeric 6-22
    private val UTR_PATTERNS = listOf(
        Pattern.compile("(?:utr|rrn|ref(?:erence)? *(?:no\\.?|#|number)?)[:\\s\\-]*([A-Z0-9]{6,22})", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(?:transaction *id|txn *id|txn *no\\.?)[:\\s\\-]*([A-Z0-9]{6,22})", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\b([0-9]{12})\\b"), // bare 12-digit UTR
    )

    private val VPA_PATTERN = Pattern.compile("\\b([a-zA-Z0-9._-]{2,40}@[a-zA-Z]{2,20})\\b")

    // Keywords confirming this is a CREDIT (received), not debit
    private val CREDIT_HINTS = listOf(
        "credited", "received", "deposited", "added to", "has been credited",
        "money received", "payment received", "you received", "got"
    )
    private val DEBIT_HINTS = listOf("debited", "sent", "paid", "deducted", "withdrawn")

    // Bug 3 fix: NPCI per-transaction UPI cap is currently ₹1 lakh for P2P and up to
    // ₹5 lakh / ₹10 lakh for specific categories (tax, IPO, insurance, hospitals,
    // education). Capping at ₹10L silently dropped legitimate large credits.
    // Raise to ₹1 crore (covers all current UPI categories with headroom) and treat
    // anything above as suspicious-but-loggable rather than silently discarded.
    private const val MAX_PLAUSIBLE_AMOUNT = 10_000_000.0  // ₹1 crore

    fun parse(text: String): Parsed? {
        if (text.isBlank()) return null
        val lower = text.lowercase()

        // Skip if it's clearly a debit/sent notification
        if (DEBIT_HINTS.any { lower.contains(it) } && CREDIT_HINTS.none { lower.contains(it) }) {
            return null
        }

        val amount = AMOUNT_PATTERNS.firstNotNullOfOrNull { p ->
            val m = p.matcher(text)
            if (m.find()) m.group(1)?.replace(",", "")?.toDoubleOrNull() else null
        } ?: return null

        if (amount <= 0 || amount > MAX_PLAUSIBLE_AMOUNT) return null

        val utr = UTR_PATTERNS.firstNotNullOfOrNull { p ->
            val m = p.matcher(text)
            if (m.find()) m.group(1)?.uppercase() else null
        }

        val vpa = VPA_PATTERN.matcher(text).let { if (it.find()) it.group(1) else null }

        return Parsed(amount, utr, vpa)
    }
}
