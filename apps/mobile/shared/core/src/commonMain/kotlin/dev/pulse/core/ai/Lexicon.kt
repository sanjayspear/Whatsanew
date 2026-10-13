package dev.pulse.core.ai

/**
 * Phrase -> structure tables for the deterministic fast path. Data, not code: Phase 2/3 ships
 * these per locale (Kannada, Hindi, Tamil...) as part of the signed model bundle, so coverage
 * grows without app releases.
 */
object Lexicon {

    /** A phrase can stand for several categories: "events" covers theatre, music, comedy, workshops. */
    data class CategoryHit(val categories: List<String>, val text: String? = null) {
        constructor(category: String, text: String? = null) : this(listOf(category), text)
    }

    /** Umbrella keys shared with the category picker so "Events" means the same everywhere. */
    val EVENTS = listOf("events", "arts", "music", "comedy")

    /** Longest phrases first so "live music" beats "music" and "south indian" beats "indian". */
    val categories: List<Pair<String, CategoryHit>> = listOf(
        // --- multi-word first ---
        "things to do" to CategoryHit(EVENTS),
        "places of worship" to CategoryHit("worship"),
        "place of worship" to CategoryHit("worship"),
        "religious places" to CategoryHit("worship"),
        "religious place" to CategoryHit("worship"),
        "jain temple" to CategoryHit("worship.jain"),
        "jain temples" to CategoryHit("worship.jain"),
        "buddhist temple" to CategoryHit("worship.buddhist"),
        "it park" to CategoryHit("business.it_park"),
        "it parks" to CategoryHit("business.it_park"),
        "tech park" to CategoryHit("business.it_park"),
        "tech parks" to CategoryHit("business.it_park"),
        "software park" to CategoryHit("business.it_park"),
        "technology park" to CategoryHit("business.it_park"),
        "business park" to CategoryHit("business.it_park"),
        "it hub" to CategoryHit("business.it_park"),
        "juice shop" to CategoryHit("food.juice"),
        "juice shops" to CategoryHit("food.juice"),
        "juice bar" to CategoryHit("food.juice"),
        "fresh juice" to CategoryHit("food.juice"),
        "ice cream" to CategoryHit("food.ice_cream"),
        "hair salon" to CategoryHit("beauty.salon"),
        "beauty parlour" to CategoryHit("beauty.salon"),
        "beauty parlor" to CategoryHit("beauty.salon"),
        "police station" to CategoryHit("services.police"),
        "post office" to CategoryHit("services.post"),
        "dry cleaning" to CategoryHit("services.laundry"),
        "dry cleaners" to CategoryHit("services.laundry"),
        "car repair" to CategoryHit("services.repair"),
        "bike repair" to CategoryHit("services.repair"),
        "car service" to CategoryHit("services.repair"),
        "bike service" to CategoryHit("services.repair"),
        "pet shop" to CategoryHit("shopping.pet"),
        "pet store" to CategoryHit("shopping.pet"),
        "provision store" to CategoryHit("shopping.grocery"),
        "bus stand" to CategoryHit("services.bus"),
        "bus station" to CategoryHit("services.bus"),
        "guest house" to CategoryHit("stay.hotel"),
        "guest houses" to CategoryHit("stay.hotel"),
        "medical store" to CategoryHit("health.pharmacy"),
        "medical shop" to CategoryHit("health.pharmacy"),
        "petrol bunk" to CategoryHit("services.fuel"),
        "petrol pump" to CategoryHit("services.fuel"),
        "ev charging" to CategoryHit("services.fuel"),
        "ethnic wear" to CategoryHit("fashion.ethnic"),
        "street wear" to CategoryHit("fashion.streetwear"),
        "shopping mall" to CategoryHit("shopping.mall"),
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
        "cinemas" to CategoryHit("arts.cinema"),
        "cinema" to CategoryHit("arts.cinema"),
        "theatres" to CategoryHit("arts.theatre"),
        "theaters" to CategoryHit("arts.theatre"),
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
        // "gym" in OSM is split across fitness centres and sports centres; people mean both.
        "gyms" to CategoryHit("fitness"),
        "gym" to CategoryHit("fitness"),
        "fitness" to CategoryHit("fitness"),
        "bookstores" to CategoryHit("shopping.books"),
        "bookstore" to CategoryHit("shopping.books"),
        "books" to CategoryHit("shopping.books"),
        "shopping" to CategoryHit("shopping"),
        "markets" to CategoryHit("shopping.market"),
        "market" to CategoryHit("shopping.market"),
        "malls" to CategoryHit("shopping.mall"),
        "mall" to CategoryHit("shopping.mall"),
        "electronics" to CategoryHit("shopping.electronics"),
        // --- fashion ---
        "fashion" to CategoryHit("fashion"),
        "clothes" to CategoryHit("fashion"),
        "clothing" to CategoryHit("fashion"),
        "apparel" to CategoryHit("fashion"),
        "boutiques" to CategoryHit("fashion.boutique"),
        "boutique" to CategoryHit("fashion.boutique"),
        "streetwear" to CategoryHit("fashion.streetwear"),
        "sarees" to CategoryHit("fashion.ethnic"),
        "saree" to CategoryHit("fashion.ethnic"),
        "sneakers" to CategoryHit("fashion.footwear"),
        "shoes" to CategoryHit("fashion.footwear"),
        "thrift" to CategoryHit("fashion.thrift"),
        // --- events (umbrella) ---
        "events" to CategoryHit(EVENTS),
        "event" to CategoryHit(EVENTS),
        "workshops" to CategoryHit("events.workshop"),
        "workshop" to CategoryHit("events.workshop"),
        "festival" to CategoryHit(EVENTS),
        "festivals" to CategoryHit(EVENTS),
        // --- food (umbrella) ---
        "food" to CategoryHit("food"),
        "eat" to CategoryHit("food"),
        "lunch" to CategoryHit("food"),
        "dinner" to CategoryHit("food"),
        "breakfast" to CategoryHit("food"),
        "bakery" to CategoryHit("food.bakery"),
        "bakeries" to CategoryHit("food.bakery"),
        "cakes" to CategoryHit("food.bakery"),
        "cake" to CategoryHit("food.bakery"),
        "pastries" to CategoryHit("food.bakery"),
        // --- fitness ---
        "workout" to CategoryHit("fitness"),
        "pilates" to CategoryHit("fitness.pilates"),
        "swimming" to CategoryHit("fitness.swimming"),
        "running" to CategoryHit("fitness.running"),
        // --- hidden gems / outdoors ---
        "parks" to CategoryHit("gems.park"),
        "park" to CategoryHit("gems.park"),
        "lakes" to CategoryHit("gems.lake"),
        "lake" to CategoryHit("gems.lake"),
        "museum" to CategoryHit("arts.museum"),
        "museums" to CategoryHit("arts.museum"),
        // --- stays (note: in Indian English "hotel" can also mean an eatery; we read it as lodging) ---
        "hotels" to CategoryHit("stay.hotel"),
        "hotel" to CategoryHit("stay.hotel"),
        "lodges" to CategoryHit("stay.hotel"),
        "lodge" to CategoryHit("stay.hotel"),
        "hostels" to CategoryHit("stay.hotel"),
        "hostel" to CategoryHit("stay.hotel"),
        "resorts" to CategoryHit("stay.hotel"),
        "resort" to CategoryHit("stay.hotel"),
        "homestay" to CategoryHit("stay.hotel"),
        "stay" to CategoryHit("stay"),
        // --- essentials ---
        "pharmacy" to CategoryHit("health.pharmacy"),
        "pharmacies" to CategoryHit("health.pharmacy"),
        "chemist" to CategoryHit("health.pharmacy"),
        "hospital" to CategoryHit("health.hospital"),
        "hospitals" to CategoryHit("health.hospital"),
        "clinic" to CategoryHit("health.hospital"),
        "clinics" to CategoryHit("health.hospital"),
        "atm" to CategoryHit("services.atm"),
        "atms" to CategoryHit("services.atm"),
        "bank" to CategoryHit("services.atm"),
        "petrol" to CategoryHit("services.fuel"),
        "fuel" to CategoryHit("services.fuel"),
        // --- places of worship (a temple search must return temples, never shops named "Temple ...") ---
        "temples" to CategoryHit("worship.temple"),
        "temple" to CategoryHit("worship.temple"),
        "mandir" to CategoryHit("worship.temple"),
        "devasthana" to CategoryHit("worship.temple"),
        "churches" to CategoryHit("worship.church"),
        "church" to CategoryHit("worship.church"),
        "chapel" to CategoryHit("worship.church"),
        "cathedral" to CategoryHit("worship.church"),
        "mosques" to CategoryHit("worship.mosque"),
        "mosque" to CategoryHit("worship.mosque"),
        "masjid" to CategoryHit("worship.mosque"),
        "dargah" to CategoryHit("worship.mosque"),
        "gurudwara" to CategoryHit("worship.gurudwara"),
        "gurdwara" to CategoryHit("worship.gurudwara"),
        "basadi" to CategoryHit("worship.jain"),
        "monastery" to CategoryHit("worship.buddhist"),
        // --- beauty ---
        "salons" to CategoryHit("beauty.salon"),
        "salon" to CategoryHit("beauty.salon"),
        "barbers" to CategoryHit("beauty.salon"),
        "barber" to CategoryHit("beauty.salon"),
        "haircut" to CategoryHit("beauty.salon"),
        "parlour" to CategoryHit("beauty.salon"),
        "parlor" to CategoryHit("beauty.salon"),
        "spa" to CategoryHit("beauty.spa"),
        "spas" to CategoryHit("beauty.spa"),
        "massage" to CategoryHit("beauty.spa"),
        "beauty" to CategoryHit("beauty"),
        "cosmetics" to CategoryHit("beauty.cosmetics"),
        // --- food extras ---
        "juice" to CategoryHit("food.juice"),
        "juices" to CategoryHit("food.juice"),
        "smoothies" to CategoryHit("food.juice"),
        "milkshake" to CategoryHit("food.juice"),
        "milkshakes" to CategoryHit("food.juice"),
        // --- everyday shopping and services ---
        "grocery" to CategoryHit("shopping.grocery"),
        "groceries" to CategoryHit("shopping.grocery"),
        "supermarket" to CategoryHit("shopping.grocery"),
        "supermarkets" to CategoryHit("shopping.grocery"),
        "kirana" to CategoryHit("shopping.grocery"),
        "vegetables" to CategoryHit("shopping.grocery"),
        "furniture" to CategoryHit("shopping.furniture"),
        "hardware" to CategoryHit("shopping.hardware"),
        "optician" to CategoryHit("health.optician"),
        "opticians" to CategoryHit("health.optician"),
        "spectacles" to CategoryHit("health.optician"),
        "laundry" to CategoryHit("services.laundry"),
        "mechanic" to CategoryHit("services.repair"),
        "garage" to CategoryHit("services.repair"),
        "police" to CategoryHit("services.police"),
        "schools" to CategoryHit("education.school"),
        "school" to CategoryHit("education.school"),
        "colleges" to CategoryHit("education.college"),
        "college" to CategoryHit("education.college"),
        "university" to CategoryHit("education.college"),
        "universities" to CategoryHit("education.college"),
        "library" to CategoryHit("education.library"),
        "libraries" to CategoryHit("education.library"),
        "stadium" to CategoryHit("sports.stadium"),
        "offices" to CategoryHit("business.office"),
        "office" to CategoryHit("business.office"),
        "companies" to CategoryHit("business.office"),
        "company" to CategoryHit("business.office"),
        "stadiums" to CategoryHit("sports.stadium"),
        "ice-cream" to CategoryHit("food.ice_cream"),
    )

    /** Words worth correcting when misspelled ("temle", "resturant"): single-word lexicon entries. */
    val correctable: Set<String> by lazy { categories.map { it.first }.filter { ' ' !in it && it.length >= 4 }.toSet() }

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
