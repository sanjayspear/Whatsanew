package dev.pulse.core.ai

/**
 * Phrase -> structure tables for the deterministic fast path. Data, not code: Phase 2/3 ships
 * these per locale (Kannada, Hindi, Tamil...) as part of the signed model bundle, so coverage
 * grows without app releases.
 */
object Lexicon {

    data class CategoryHit(val category: String, val text: String? = null)

    /** Longest phrases first so "live music" beats "music" and "south indian" beats "indian". */
    val categories: List<Pair<String, CategoryHit>> = listOf(
        "south indian" to CategoryHit("food.restaurant.south_indian"),
        "live music" to CategoryHit("music.live"),
        "stand up" to CategoryHit("comedy.standup"),
        "stand-up" to CategoryHit("comedy.standup"),
        "standup" to CategoryHit("comedy.standup"),
        "hidden gems" to CategoryHit("gems"),
        "hidden gem" to CategoryHit("gems"),
        "fine dining" to CategoryHit("food.restaurant.fine_dining"),
        "flea market" to CategoryHit("shopping.market"),
        "coffee" to CategoryHit("food.cafe"),
        "cafes" to CategoryHit("food.cafe"),
        "cafe" to CategoryHit("food.cafe"),
        "café" to CategoryHit("food.cafe"),
        "dosa" to CategoryHit("food.restaurant.south_indian", text = "dosa"),
        "idli" to CategoryHit("food.restaurant.south_indian", text = "idli"),
        "biryani" to CategoryHit("food.restaurant", text = "biryani"),
        "restaurants" to CategoryHit("food.restaurant"),
        "restaurant" to CategoryHit("food.restaurant"),
        "breweries" to CategoryHit("food.bar"),
        "brewery" to CategoryHit("food.bar"),
        "pubs" to CategoryHit("food.bar"),
        "pub" to CategoryHit("food.bar"),
        "bars" to CategoryHit("food.bar"),
        "drama" to CategoryHit("arts.theatre"),
        "dramas" to CategoryHit("arts.theatre"),
        "theatre" to CategoryHit("arts.theatre"),
        "theater" to CategoryHit("arts.theatre"),
        "plays" to CategoryHit("arts.theatre"),
        "play" to CategoryHit("arts.theatre"),
        "cinema" to CategoryHit("arts.cinema"),
        "movies" to CategoryHit("arts.cinema"),
        "movie" to CategoryHit("arts.cinema"),
        "films" to CategoryHit("arts.cinema"),
        "gallery" to CategoryHit("arts.gallery"),
        "exhibition" to CategoryHit("arts.gallery"),
        "concerts" to CategoryHit("music.live"),
        "concert" to CategoryHit("music.live"),
        "gigs" to CategoryHit("music.live"),
        "gig" to CategoryHit("music.live"),
        "comedy" to CategoryHit("comedy.standup"),
        "yoga" to CategoryHit("fitness.yoga"),
        "gyms" to CategoryHit("fitness.gym"),
        "gym" to CategoryHit("fitness.gym"),
        "fitness" to CategoryHit("fitness"),
        "bookstores" to CategoryHit("shopping.books"),
        "bookstore" to CategoryHit("shopping.books"),
        "books" to CategoryHit("shopping.books"),
        "shopping" to CategoryHit("shopping"),
        "markets" to CategoryHit("shopping.market"),
        "market" to CategoryHit("shopping.market"),
    )

    val freshNew = listOf("newly opened", "just opened", "new", "latest", "newest")
    val freshTrending = listOf("trending", "popular", "hot right now", "buzzing")
    val cheap = listOf("cheap", "budget", "affordable", "inexpensive", "pocket friendly")
    val openNow = listOf("open now", "open right now", "still open")
    val hereWords = listOf("near me", "nearby", "around me", "close to me", "around here")

    val timePresets = listOf(
        "this weekend" to "THIS_WEEKEND",
        "weekend" to "THIS_WEEKEND",
        "saturday" to "THIS_WEEKEND",
        "sunday" to "THIS_WEEKEND",
        "tonight" to "TONIGHT",
        "this evening" to "TONIGHT",
        "tomorrow" to "TOMORROW",
        "today" to "TODAY",
        "this week" to "NEXT_7_DAYS",
        "next few days" to "NEXT_7_DAYS",
        "right now" to "NOW",
    )

    // ---- Customization ----
    val themeNouns = listOf("theme", "colour", "color", "colours", "colors", "look", "vibe", "mode", "appearance", "skin")
    val moods = listOf(
        "warm" to "WARM", "cosy" to "WARM", "cozy" to "WARM", "sunset" to "WARM",
        "calm" to "CALM", "minimal" to "CALM", "soft" to "CALM",
        "vivid" to "VIVID", "bright" to "VIVID", "colourful" to "VIVID", "colorful" to "VIVID",
        "noir" to "NOIR", "dark" to "NOIR", "black" to "NOIR",
        "light" to "DAYLIGHT", "daylight" to "DAYLIGHT", "white" to "DAYLIGHT",
        "dusk" to "DUSK", "default" to "DUSK", "original" to "DUSK",
    )
    val quieter = listOf("quieter", "quiet", "less motion", "calmer", "subtle", "less busy", "relaxed")
    val louder = listOf("livelier", "more motion", "punchier", "energetic")
    val biggerText = listOf("bigger text", "larger text", "increase text", "bigger font", "larger font")
    val smallerText = listOf("smaller text", "decrease text", "smaller font")

    val layoutVerbs = listOf("move", "put", "bring", "place", "show", "hide", "remove", "drop")
    val roles = listOf(
        "map" to "map", "maps" to "map",
        "videos" to "videos", "video" to "videos", "trailers" to "videos",
        "list" to "results", "results" to "results", "cards" to "results", "listings" to "results",
        "header" to "header", "title" to "header", "summary" to "header",
    )
    val toTop = listOf("to the top", "at the top", "on top", "top", "first", "up")
    val toBottom = listOf("to the bottom", "at the bottom", "bottom", "last", "down", "end")

    val refineStarts = listOf("only", "just ", "but ", "also ", "cheaper", "closer", "now only", "filter")
    val actVerbs = listOf("call", "directions", "navigate", "book", "share", "save")
    val ordinals = listOf("first" to 0, "top" to 0, "nearest" to 0, "second" to 1, "third" to 2)
}
