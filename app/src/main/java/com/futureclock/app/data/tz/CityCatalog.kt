package com.futureclock.app.data.tz

data class City(
    val name: String,
    val country: String,
    val flag: String,
    val tzId: String
)

/** Lightweight curated city catalog (~150 cities) with IANA timezones and country flags. */
object CityCatalog {

    val ALL: List<City> by lazy { buildCatalog() }

    fun search(query: String): List<City> {
        if (query.isBlank()) return ALL
        val q = query.trim().lowercase()
        return ALL.filter {
            it.name.lowercase().contains(q) ||
            it.country.lowercase().contains(q) ||
            it.tzId.lowercase().contains(q)
        }
    }

    private fun buildCatalog(): List<City> = listOf(
        // North America
        City("New York", "United States", "\uD83C\uDDFA\uD83C\uDDF8", "America/New_York"),
        City("Los Angeles", "United States", "\uD83C\uDDFA\uD83C\uDDF8", "America/Los_Angeles"),
        City("Chicago", "United States", "\uD83C\uDDFA\uD83C\uDDF8", "America/Chicago"),
        City("Denver", "United States", "\uD83C\uDDFA\uD83C\uDDF8", "America/Denver"),
        City("Phoenix", "United States", "\uD83C\uDDFA\uD83C\uDDF8", "America/Phoenix"),
        City("Anchorage", "United States", "\uD83C\uDDFA\uD83C\uDDF8", "America/Anchorage"),
        City("Honolulu", "United States", "\uD83C\uDDFA\uD83C\uDDF8", "Pacific/Honolulu"),
        City("Toronto", "Canada", "\uD83C\uDDE8\uD83C\uDDE6", "America/Toronto"),
        City("Vancouver", "Canada", "\uD83C\uDDE8\uD83C\uDDE6", "America/Vancouver"),
        City("Montreal", "Canada", "\uD83C\uDDE8\uD83C\uDDE6", "America/Montreal"),
        City("Mexico City", "Mexico", "\uD83C\uDDF2\uD83C\uDDFD", "America/Mexico_City"),

        // South America
        City("São Paulo", "Brazil", "\uD83C\uDDE7\uD83C\uDDF7", "America/Sao_Paulo"),
        City("Rio de Janeiro", "Brazil", "\uD83C\uDDE7\uD83C\uDDF7", "America/Sao_Paulo"),
        City("Brasília", "Brazil", "\uD83C\uDDE7\uD83C\uDDF7", "America/Sao_Paulo"),
        City("Buenos Aires", "Argentina", "\uD83C\uDDE6\uD83C\uDDF7", "America/Argentina/Buenos_Aires"),
        City("Santiago", "Chile", "\uD83C\uDDE8\uD83C\uDDF1", "America/Santiago"),
        City("Lima", "Peru", "\uD83C\uDDF5\uD83C\uDDEA", "America/Lima"),
        City("Bogotá", "Colombia", "\uD83C\uDDE8\uD83C\uDDF4", "America/Bogota"),
        City("Caracas", "Venezuela", "\uD83C\uDDFB\uD83C\uDDEA", "America/Caracas"),

        // Europe
        City("London", "United Kingdom", "\uD83C\uDDEC\uD83C\uDDE7", "Europe/London"),
        City("Dublin", "Ireland", "\uD83C\uDDEE\uD83C\uDDEA", "Europe/Dublin"),
        City("Paris", "France", "\uD83C\uDDEB\uD83C\uDDF7", "Europe/Paris"),
        City("Berlin", "Germany", "\uD83C\uDDE9\uD83C\uDDEA", "Europe/Berlin"),
        City("Munich", "Germany", "\uD83C\uDDE9\uD83C\uDDEA", "Europe/Berlin"),
        City("Hamburg", "Germany", "\uD83C\uDDE9\uD83C\uDDEA", "Europe/Berlin"),
        City("Frankfurt", "Germany", "\uD83C\uDDE9\uD83C\uDDEA", "Europe/Berlin"),
        City("Madrid", "Spain", "\uD83C\uDDEA\uD83C\uDDF8", "Europe/Madrid"),
        City("Barcelona", "Spain", "\uD83C\uDDEA\uD83C\uDDF8", "Europe/Madrid"),
        City("Lisbon", "Portugal", "\uD83C\uDDF5\uD83C\uDDF9", "Europe/Lisbon"),
        City("Rome", "Italy", "\uD83C\uDDEE\uD83C\uDDF9", "Europe/Rome"),
        City("Milan", "Italy", "\uD83C\uDDEE\uD83C\uDDF9", "Europe/Rome"),
        City("Vienna", "Austria", "\uD83C\uDDE6\uD83C\uDDF9", "Europe/Vienna"),
        City("Zurich", "Switzerland", "\uD83C\uDDE8\uD83C\uDDED", "Europe/Zurich"),
        City("Geneva", "Switzerland", "\uD83C\uDDE8\uD83C\uDDED", "Europe/Zurich"),
        City("Amsterdam", "Netherlands", "\uD83C\uDDF3\uD83C\uDDF1", "Europe/Amsterdam"),
        City("Brussels", "Belgium", "\uD83C\uDDE7\uD83C\uDDEA", "Europe/Brussels"),
        City("Copenhagen", "Denmark", "\uD83C\uDDE9\uD83C\uDDF0", "Europe/Copenhagen"),
        City("Stockholm", "Sweden", "\uD83C\uDDF8\uD83C\uDDEA", "Europe/Stockholm"),
        City("Oslo", "Norway", "\uD83C\uDDF3\uD83C\uDDF4", "Europe/Oslo"),
        City("Helsinki", "Finland", "\uD83C\uDDEB\uD83C\uDDEE", "Europe/Helsinki"),
        City("Reykjavik", "Iceland", "\uD83C\uDDEE\uD83C\uDDF8", "Atlantic/Reykjavik"),
        City("Athens", "Greece", "\uD83C\uDDEC\uD83C\uDDF7", "Europe/Athens"),
        City("Warsaw", "Poland", "\uD83C\uDDF5\uD83C\uDDF1", "Europe/Warsaw"),
        City("Prague", "Czechia", "\uD83C\uDDE8\uD83C\uDDFF", "Europe/Prague"),
        City("Budapest", "Hungary", "\uD83C\uDDED\uD83C\uDDFA", "Europe/Budapest"),
        City("Bucharest", "Romania", "\uD83C\uDDF7\uD83C\uDDF4", "Europe/Bucharest"),
        City("Istanbul", "Türkiye", "\uD83C\uDDF9\uD83C\uDDF7", "Europe/Istanbul"),
        City("Moscow", "Russia", "\uD83C\uDDF7\uD83C\uDDFA", "Europe/Moscow"),
        City("Saint Petersburg", "Russia", "\uD83C\uDDF7\uD83C\uDDFA", "Europe/Moscow"),
        City("Kyiv", "Ukraine", "\uD83C\uDDFA\uD83C\uDDE6", "Europe/Kyiv"),

        // Africa
        City("Cairo", "Egypt", "\uD83C\uDDFA\uD83C\uDDEC", "Africa/Cairo"),
        City("Lagos", "Nigeria", "\uD83C\uDDF3\uD83C\uDDEC", "Africa/Lagos"),
        City("Nairobi", "Kenya", "\uD83C\uDDF0\uD83C\uDDEA", "Africa/Nairobi"),
        City("Cape Town", "South Africa", "\uD83C\uDDFF\uD83C\uDDE6", "Africa/Johannesburg"),
        City("Johannesburg", "South Africa", "\uD83C\uDDFF\uD83C\uDDE6", "Africa/Johannesburg"),
        City("Casablanca", "Morocco", "\uD83C\uDDF2\uD83C\uDDE6", "Africa/Casablanca"),
        City("Algiers", "Algeria", "\uD83C\uDDF9\uD83C\uDDFF", "Africa/Algiers"),
        City("Tunis", "Tunisia", "\uD83C\uDDF9\uD83C\uDDF3", "Africa/Tunis"),
        City("Addis Ababa", "Ethiopia", "\uD83C\uDDEA\uD83C\uDDF9", "Africa/Addis_Ababa"),

        // Middle East
        City("Dubai", "United Arab Emirates", "\uD83C\uDDE6\uD83C\uDDEA", "Asia/Dubai"),
        City("Abu Dhabi", "United Arab Emirates", "\uD83C\uDDE6\uD83C\uDDEA", "Asia/Dubai"),
        City("Riyadh", "Saudi Arabia", "\uD83C\uDDF8\uD83C\uDDE6", "Asia/Riyadh"),
        City("Jeddah", "Saudi Arabia", "\uD83C\uDDF8\uD83C\uDDE6", "Asia/Riyadh"),
        City("Doha", "Qatar", "\uD83C\uDDF6\uD83C\uDDE6", "Asia/Qatar"),
        City("Kuwait City", "Kuwait", "\uD83C\uDDF0\uD83C\uDDFC", "Asia/Kuwait"),
        City("Manama", "Bahrain", "\uD83C\uDDE7\uD83C\uDDE7", "Asia/Bahrain"),
        City("Muscat", "Oman", "\uD83C\uDDF4\uD83C\uDDF2", "Asia/Muscat"),
        City("Amman", "Jordan", "\uD83C\uDDEF\uD83C\uDDED", "Asia/Amman"),
        City("Beirut", "Lebanon", "\uD83C\uDDF1\uD83C\uDDE7", "Asia/Beirut"),
        City("Tel Aviv", "Israel", "\uD83C\uDDEE\uD83C\uDDF1", "Asia/Jerusalem"),
        City("Jerusalem", "Israel", "\uD83C\uDDEE\uD83C\uDDF1", "Asia/Jerusalem"),
        City("Tehran", "Iran", "\uD83C\uDDEE\uD83C\uDDF7", "Asia/Tehran"),
        City("Baghdad", "Iraq", "\uD83C\uDDE6\uD83C\uDDE6", "Asia/Baghdad"),

        // South & Central Asia
        City("Mumbai", "India", "\uD83C\uDDEE\uD83C\uDDF3", "Asia/Kolkata"),
        City("Delhi", "India", "\uD83C\uDDEE\uD83C\uDDF3", "Asia/Kolkata"),
        City("Bangalore", "India", "\uD83C\uDDEE\uD83C\uDDF3", "Asia/Kolkata"),
        City("Kolkata", "India", "\uD83C\uDDEE\uD83C\uDDF3", "Asia/Kolkata"),
        City("Chennai", "India", "\uD83C\uDDEE\uD83C\uDDF3", "Asia/Kolkata"),
        City("Hyderabad", "India", "\uD83C\uDDEE\uD83C\uDDF3", "Asia/Kolkata"),
        City("Karachi", "Pakistan", "\uD83C\uDDF5\uD83C\uDDF0", "Asia/Karachi"),
        City("Lahore", "Pakistan", "\uD83C\uDDF5\uD83C\uDDF0", "Asia/Karachi"),
        City("Islamabad", "Pakistan", "\uD83C\uDDF5\uD83C\uDDF0", "Asia/Karachi"),
        City("Dhaka", "Bangladesh", "\uD83C\uDDE9\uD83C\uDDE9", "Asia/Dhaka"),
        City("Colombo", "Sri Lanka", "\uD83C\uDDF1\uD83C\uDDF0", "Asia/Colombo"),
        City("Kathmandu", "Nepal", "\uD83C\uDDF3\uD83C\uDDF5", "Asia/Kathmandu"),

        // East & Southeast Asia
        City("Beijing", "China", "\uD83C\uDDE8\uD83C\uDDF3", "Asia/Shanghai"),
        City("Shanghai", "China", "\uD83C\uDDE8\uD83C\uDDF3", "Asia/Shanghai"),
        City("Shenzhen", "China", "\uD83C\uDDE8\uD83C\uDDF3", "Asia/Shanghai"),
        City("Hong Kong", "China", "\uD83C\uDDED\uD83C\uDDF0", "Asia/Hong_Kong"),
        City("Taipei", "Taiwan", "\uD83C\uDDF9\uD83C\uDDFC", "Asia/Taipei"),
        City("Tokyo", "Japan", "\uD83C\uDDEF\uD83C\uDDF5", "Asia/Tokyo"),
        City("Osaka", "Japan", "\uD83C\uDDEF\uD83C\uDDF5", "Asia/Tokyo"),
        City("Kyoto", "Japan", "\uD83C\uDDEF\uD83C\uDDF5", "Asia/Tokyo"),
        City("Seoul", "South Korea", "\uD83C\uDDF0\uD83C\uDDF7", "Asia/Seoul"),
        City("Busan", "South Korea", "\uD83C\uDDF0\uD83C\uDDF7", "Asia/Seoul"),
        City("Pyongyang", "North Korea", "\uD83C\uDDF0\uD83C\uDDF5", "Asia/Pyongyang"),
        City("Singapore", "Singapore", "\uD83C\uDDF8\uD83C\uDDEC", "Asia/Singapore"),
        City("Kuala Lumpur", "Malaysia", "\uD83C\uDDF2\uD83C\uDDFE", "Asia/Kuala_Lumpur"),
        City("Bangkok", "Thailand", "\uD83C\uDDF9\uD83C\uDDED", "Asia/Bangkok"),
        City("Jakarta", "Indonesia", "\uD83C\uDDEE\uD83C\uDDE9", "Asia/Jakarta"),
        City("Bali", "Indonesia", "\uD83C\uDDEE\uD83C\uDDE9", "Asia/Makassar"),
        City("Manila", "Philippines", "\uD83C\uDDF5\uD83C\uDDED", "Asia/Manila"),
        City("Hanoi", "Vietnam", "\uD83C\uDDFB\uD83C\uDDF3", "Asia/Ho_Chi_Minh"),
        City("Ho Chi Minh City", "Vietnam", "\uD83C\uDDFB\uD83C\uDDF3", "Asia/Ho_Chi_Minh"),

        // Oceania
        City("Sydney", "Australia", "\uD83C\uDDE6\uD83C\uDDFA", "Australia/Sydney"),
        City("Melbourne", "Australia", "\uD83C\uDDE6\uD83C\uDDFA", "Australia/Melbourne"),
        City("Brisbane", "Australia", "\uD83C\uDDE6\uD83C\uDDFA", "Australia/Brisbane"),
        City("Perth", "Australia", "\uD83C\uDDE6\uD83C\uDDFA", "Australia/Perth"),
        City("Adelaide", "Australia", "\uD83C\uDDE6\uD83C\uDDFA", "Australia/Adelaide"),
        City("Auckland", "New Zealand", "\uD83C\uDDF3\uD83C\uDDFF", "Pacific/Auckland"),
        City("Wellington", "New Zealand", "\uD83C\uDDF3\uD83C\uDDFF", "Pacific/Auckland"),
        City("Fiji", "Fiji", "\uD83C\uDDEB\uD83C\uDDEF", "Pacific/Fiji"),

        // UTC
        City("UTC", "Coordinated Universal Time", "\uD83C\uDFF4\uDB40\uDC67\uDB40\uDC62\uDB40\uDC65\uDB40\uDC6E\uDB40\uDC67\uDB40\uDC7F", "UTC")
    )
}
