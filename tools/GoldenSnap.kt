import com.farol.app.core.OfferParser
fun main() {
    generateSequence(::readLine).forEach { line ->
        val i = line.indexOf('\t'); val app = line.substring(0, i)
        val text = line.substring(i + 1).replace("\\n", "\n").replace("\\t", "\t")
        val o = OfferParser.parse(text, app)
        println(if (o == null) "null" else listOf(o.price, o.totalKm, o.totalMin ?: -1.0, o.pickupKm ?: -1.0, o.passengerRating ?: -1.0).joinToString("\t") { "%.2f".format(java.util.Locale.US, it) })
    }
}
