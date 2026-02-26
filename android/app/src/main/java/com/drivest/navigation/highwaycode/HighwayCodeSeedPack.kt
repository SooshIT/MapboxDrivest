package com.drivest.navigation.highwaycode

internal fun buildDefaultHighwayCodePack(): HighwayCodePack {
    val categories = listOf(
        HighwayCodeCategory(id = "speed_limits", name = "Speed limits", questionCount = 4),
        HighwayCodeCategory(id = "junctions", name = "Junctions and crossings", questionCount = 4),
        HighwayCodeCategory(id = "roundabouts", name = "Roundabouts", questionCount = 4),
        HighwayCodeCategory(id = "signals", name = "Signals and mirrors", questionCount = 4),
        HighwayCodeCategory(id = "lane_discipline", name = "Lane discipline", questionCount = 4),
        HighwayCodeCategory(id = "vulnerable", name = "Vulnerable road users", questionCount = 4),
        HighwayCodeCategory(id = "motorways", name = "Motorways and smart motorways", questionCount = 4),
        HighwayCodeCategory(id = "weather_emergency", name = "Weather and emergencies", questionCount = 8)
    )

    val questions = listOf(
        question(
            1,
            "speed_limits",
            "easy",
            "You are driving a car on a single carriageway road in England or Wales and there are no lower limit signs.",
            "What is the national speed limit for your car?",
            listOf("50 mph", "60 mph", "70 mph", "30 mph"),
            1,
            "For cars and motorcycles, the national speed limit on a single carriageway is 60 mph unless signs show a lower limit.",
            "Speed limits"
        ),
        question(
            2,
            "speed_limits",
            "easy",
            "You enter a built-up road with street lights and no speed limit signs visible.",
            "What limit usually applies?",
            listOf("20 mph", "30 mph", "40 mph", "50 mph"),
            1,
            "A system of street lighting usually means a 30 mph road unless signs indicate another limit.",
            "Speed limits"
        ),
        question(
            3,
            "speed_limits",
            "medium",
            "You are on a dual carriageway in a car with no lower speed signs.",
            "What is the national speed limit?",
            listOf("60 mph", "70 mph", "50 mph", "80 mph"),
            1,
            "For cars and motorcycles, the national speed limit on a dual carriageway is 70 mph unless signs show a lower limit.",
            "Speed limits"
        ),
        question(
            4,
            "speed_limits",
            "easy",
            "A sign shows a red circle with the number 40.",
            "What does this sign mean?",
            listOf(
                "Advisory speed of 40 mph",
                "Minimum speed of 40 mph",
                "Maximum speed of 40 mph",
                "Average speed camera zone starts"
            ),
            2,
            "A number inside a red circle is a mandatory maximum speed limit.",
            "Traffic signs / Speed limits"
        ),
        question(
            5,
            "junctions",
            "medium",
            "You are turning into a side road and pedestrians are crossing the road you are turning into.",
            "What should you do?",
            listOf(
                "Continue because you are already turning",
                "Give way and let them cross",
                "Sound your horn to warn them",
                "Only stop if they are in your lane"
            ),
            1,
            "When turning into a road, give way to pedestrians crossing or waiting to cross that road.",
            "Junctions"
        ),
        question(
            6,
            "junctions",
            "medium",
            "You approach a yellow box junction and your exit road is blocked by traffic.",
            "What should you do?",
            listOf(
                "Enter and wait in the box",
                "Do not enter until your exit is clear",
                "Enter slowly and stop halfway",
                "Use your horn and proceed"
            ),
            1,
            "Do not enter a box junction unless your exit road is clear.",
            "Road markings / Junctions"
        ),
        question(
            7,
            "junctions",
            "easy",
            "Traffic lights change to steady amber and you have not crossed the stop line yet.",
            "What should you normally do?",
            listOf(
                "Accelerate to get through",
                "Stop unless it would be unsafe to do so",
                "Proceed if the junction looks clear",
                "Continue only if a car is behind you"
            ),
            1,
            "Steady amber means stop at the stop line unless stopping might cause a collision.",
            "Traffic lights"
        ),
        question(
            8,
            "junctions",
            "medium",
            "You are waiting to turn right at a junction while oncoming traffic passes.",
            "How should you hold your steering while waiting?",
            listOf(
                "Wheels turned right ready to go",
                "Wheels straight ahead",
                "Wheels turned left",
                "It does not matter"
            ),
            1,
            "Keep your wheels straight while waiting to turn right, reducing the risk of being pushed into oncoming traffic if hit from behind.",
            "Junctions / Positioning"
        ),
        question(
            9,
            "roundabouts",
            "easy",
            "You plan to take the first exit at a normal roundabout.",
            "How should you signal on approach?",
            listOf(
                "No signal",
                "Signal left",
                "Signal right",
                "Use hazard lights"
            ),
            1,
            "For the first exit, signal left on approach and keep left unless road markings show otherwise.",
            "Roundabouts"
        ),
        question(
            10,
            "roundabouts",
            "medium",
            "You are taking an exit to the right at a standard roundabout.",
            "Which signal plan is usually correct?",
            listOf(
                "Signal right on approach, then signal left after passing the exit before yours",
                "Signal left on approach only",
                "No signals at all",
                "Hazard lights while circulating"
            ),
            0,
            "For exits to the right, signal right on approach and change to left signal after passing the exit before the one you want.",
            "Roundabouts"
        ),
        question(
            11,
            "roundabouts",
            "medium",
            "At a mini-roundabout, another vehicle approaches from your right.",
            "Who normally has priority?",
            listOf(
                "You, because it is a mini-roundabout",
                "The vehicle on your right",
                "Whoever reaches the line first",
                "The larger vehicle"
            ),
            1,
            "At mini-roundabouts, give way to traffic from the right unless signs or road layout direct otherwise.",
            "Mini-roundabouts"
        ),
        question(
            12,
            "roundabouts",
            "medium",
            "Lane markings on a roundabout direct your lane to a specific exit.",
            "What should you do?",
            listOf(
                "Ignore the markings and choose later",
                "Follow the lane markings and signs",
                "Stop on the roundabout to change lanes",
                "Use any lane if traffic is light"
            ),
            1,
            "Follow lane markings and signs. Plan early rather than changing suddenly while on the roundabout.",
            "Roundabouts / Lane discipline"
        ),
        question(
            13,
            "signals",
            "easy",
            "You are moving off from the side of the road.",
            "Which routine is best?",
            listOf(
                "Signal and move immediately",
                "Mirror, signal, manoeuvre with all-round checks",
                "Look ahead and go",
                "Horn, signal, move"
            ),
            1,
            "Use mirror-signal-manoeuvre and include blind-spot checks before moving off.",
            "Signals and mirrors"
        ),
        question(
            14,
            "signals",
            "medium",
            "Another driver flashes headlights to let you out.",
            "How should you treat the flash?",
            listOf(
                "As a guarantee the road is clear",
                "As a command to move immediately",
                "As a signal they are there; still check it is safe",
                "As permission to ignore pedestrians"
            ),
            2,
            "Headlight flashing is not a command. Check the road yourself and move only when safe.",
            "Signals"
        ),
        question(
            15,
            "signals",
            "easy",
            "You plan to change lanes on a dual carriageway.",
            "What should you do first?",
            listOf(
                "Signal only and move",
                "Check mirrors and blind spot before moving",
                "Steer first, then check mirrors",
                "Accelerate sharply"
            ),
            1,
            "Before changing lanes, check mirrors and blind spot, then signal if needed and move only when safe.",
            "Lane changes / Mirrors"
        ),
        question(
            16,
            "signals",
            "medium",
            "You realise your indicator is still on after a turn.",
            "Why is this dangerous?",
            listOf(
                "It drains fuel",
                "It can mislead other road users",
                "It makes your brake lights fail",
                "It is only a problem at night"
            ),
            1,
            "Misleading signals can cause others to make unsafe decisions based on false assumptions.",
            "Signals"
        ),
        question(
            17,
            "lane_discipline",
            "easy",
            "On a multi-lane road with light traffic, which lane should you normally use?",
            "Choose the best answer.",
            listOf(
                "Right lane for better view",
                "Middle lane to avoid merging later",
                "Left lane unless overtaking",
                "Any lane at random"
            ),
            2,
            "Keep left unless overtaking or directed otherwise by signs or lane markings.",
            "Lane discipline"
        ),
        question(
            18,
            "lane_discipline",
            "medium",
            "You miss your motorway exit.",
            "What should you do?",
            listOf(
                "Reverse on the hard shoulder",
                "Stop and wait for a gap",
                "Continue to the next junction and re-route",
                "Make a U-turn at the next opening"
            ),
            2,
            "Never reverse or make a U-turn on a motorway. Continue safely and take the next exit.",
            "Motorways / Lane discipline"
        ),
        question(
            19,
            "lane_discipline",
            "medium",
            "You are in the middle lane of a motorway and the left lane is clear for a long distance.",
            "Best practice is to:",
            listOf(
                "Stay in the middle lane",
                "Move back to the left lane when safe",
                "Move to the right lane for visibility",
                "Slow down and stop in a refuge area"
            ),
            1,
            "Return to the left lane when not overtaking. Staying in the middle lane unnecessarily can be careless driving.",
            "Motorway lane discipline"
        ),
        question(
            20,
            "lane_discipline",
            "easy",
            "A lane arrow shows you must turn left.",
            "What does that mean?",
            listOf(
                "It is advisory only",
                "Only buses must follow it",
                "You should follow the lane direction shown",
                "You can go straight if traffic is light"
            ),
            2,
            "Lane arrows and road markings are used to place traffic safely. Follow them.",
            "Road markings"
        ),
        question(
            21,
            "vulnerable",
            "medium",
            "You are overtaking a cyclist on a 30 mph road.",
            "What side clearance should you aim to leave where possible?",
            listOf(
                "About 0.5 m",
                "At least 1.0 m",
                "At least 1.5 m",
                "As close as possible if quick"
            ),
            2,
            "The Highway Code advises at least 1.5 m when overtaking cyclists at up to 30 mph, where possible.",
            "Cyclists"
        ),
        question(
            22,
            "vulnerable",
            "medium",
            "You approach a horse rider on a narrow road.",
            "What is the safest approach?",
            listOf(
                "Pass quickly before the horse moves",
                "Rev the engine to warn them",
                "Slow down, leave space, and be patient",
                "Use high beam so they can see you"
            ),
            2,
            "Horses can be startled. Slow down, keep a large gap, and pass carefully only when safe.",
            "Horse riders"
        ),
        question(
            23,
            "vulnerable",
            "easy",
            "A pedestrian is already on a zebra crossing.",
            "What must you do?",
            listOf(
                "Stop and give way",
                "Proceed if they are moving slowly",
                "Sound your horn only",
                "Pass if the opposite lane is clear"
            ),
            0,
            "You must give way to pedestrians on a zebra crossing.",
            "Pedestrian crossings"
        ),
        question(
            24,
            "vulnerable",
            "medium",
            "You are turning and a cyclist is going straight ahead across your path.",
            "What should you do?",
            listOf(
                "Turn before the cyclist reaches you",
                "Give way and wait",
                "Use the horn so they stop",
                "Edge forward to claim priority"
            ),
            1,
            "Take care not to cut across cyclists going straight ahead. Give way and turn only when safe.",
            "Cyclists / Junctions"
        ),
        question(
            25,
            "motorways",
            "easy",
            "A red X is shown above a motorway lane.",
            "What does it mean?",
            listOf(
                "Average speed camera active",
                "Lane closed: do not use it",
                "Lane for heavy vehicles only",
                "Toll lane starts"
            ),
            1,
            "A red X means the lane is closed. You must not drive in that lane.",
            "Motorways / Signals"
        ),
        question(
            26,
            "motorways",
            "medium",
            "You break down on a smart motorway and can reach an emergency refuge area.",
            "What should you do?",
            listOf(
                "Stop in a live lane and wait",
                "Use the refuge area, stop safely, and get help",
                "Reverse to the previous exit",
                "Stay in lane and call later"
            ),
            1,
            "If possible, leave the live lane and use an emergency refuge area before calling for help.",
            "Smart motorways / Breakdowns"
        ),
        question(
            27,
            "motorways",
            "medium",
            "A mandatory speed limit is shown in a red circle on an overhead motorway sign.",
            "How should you respond?",
            listOf(
                "Treat it as advice only",
                "Comply with the displayed limit",
                "Ignore it if traffic is light",
                "Only lorries must obey it"
            ),
            1,
            "A speed limit in a red circle is mandatory, including variable motorway limits.",
            "Motorways / Variable speed limits"
        ),
        question(
            28,
            "motorways",
            "easy",
            "You miss your motorway exit.",
            "What should you do next?",
            listOf(
                "Reverse on the hard shoulder",
                "Stop and wait near the slip road",
                "Continue to the next junction",
                "Make a U-turn"
            ),
            2,
            "Continue to the next junction and re-route. Never reverse or turn around on a motorway.",
            "Motorways"
        ),
        question(
            29,
            "weather_emergency",
            "easy",
            "Heavy rain starts and the road surface is wet.",
            "How are stopping distances affected?",
            listOf(
                "They stay the same",
                "They are usually shorter",
                "They can be at least double",
                "Only tyre wear changes"
            ),
            2,
            "Stopping distances increase in wet conditions; a common rule is at least double compared with dry roads.",
            "Weather conditions"
        ),
        question(
            30,
            "weather_emergency",
            "medium",
            "Visibility is seriously reduced in fog.",
            "Which lights should you use?",
            listOf(
                "Full beam only",
                "Dipped headlights, and fog lights if needed",
                "Sidelights only",
                "Hazard lights while moving"
            ),
            1,
            "Use dipped headlights in fog. Fog lights may be used when visibility is seriously reduced.",
            "Fog"
        ),
        question(
            31,
            "weather_emergency",
            "medium",
            "Roads are icy.",
            "Compared with dry roads, stopping distances can be:",
            listOf(
                "About the same",
                "Up to 10 times longer",
                "Half as long",
                "Only longer above 60 mph"
            ),
            1,
            "On ice, stopping distances can be dramatically longer, potentially up to ten times longer than on dry roads.",
            "Weather conditions"
        ),
        question(
            32,
            "weather_emergency",
            "easy",
            "You hear a siren and see blue lights behind you.",
            "What should you do?",
            listOf(
                "Brake sharply immediately",
                "Stay calm, check mirrors, and move aside safely",
                "Speed up to create space",
                "Ignore it until the next junction"
            ),
            1,
            "Give priority to emergency vehicles when safe. Stay calm and avoid creating danger while moving aside.",
            "Emergency vehicles"
        ),
        question(
            33,
            "weather_emergency",
            "medium",
            "Your car breaks down on a high-speed road and you cannot leave immediately.",
            "What is your priority?",
            listOf(
                "Keep trying to restart in-lane",
                "Warn traffic and seek help while staying as safe as possible",
                "Stand behind the car in the lane",
                "Walk in the carriageway to direct traffic"
            ),
            1,
            "Prioritise safety: warn others if appropriate, call for help, and move to a safer place away from traffic when possible.",
            "Breakdowns"
        ),
        question(
            34,
            "weather_emergency",
            "medium",
            "You are dazzled by oncoming headlights at night.",
            "What should you do?",
            listOf(
                "Look directly at the lights",
                "Slow down and look toward the left edge/markings",
                "Flash full beam back",
                "Close one eye and continue at the same speed"
            ),
            1,
            "Reduce speed and avoid staring at headlights. Use the left road edge or markings to maintain your position.",
            "Night driving"
        ),
        question(
            35,
            "weather_emergency",
            "easy",
            "When should hazard warning lights be used while moving?",
            "Pick the best answer.",
            listOf(
                "Whenever traffic is slow",
                "To thank another driver",
                "To warn traffic behind of a hazard or obstruction ahead",
                "Whenever you are unsure"
            ),
            2,
            "Hazard lights are for warning of danger. Do not use them casually while driving.",
            "Signals / Hazards"
        ),
        question(
            36,
            "weather_emergency",
            "easy",
            "Your windscreen is misting up while driving.",
            "What should you do?",
            listOf(
                "Use demisters and airflow, and keep visibility clear",
                "Wipe it with your hand while driving one-handed",
                "Ignore it until the next stop",
                "Use full beam to improve visibility"
            ),
            0,
            "Keep windows clear using demisters and ventilation. Do not continue if visibility becomes unsafe.",
            "Vehicle condition / Weather"
        )
    )

    return HighwayCodePack(
        categories = categories,
        questions = questions,
        sourceReferences = listOf(
            "https://www.gov.uk/guidance/the-highway-code",
            "https://play.google.com/store/apps/details?id=com.glenmax.highwaycode"
        )
    )
}

private fun question(
    id: Int,
    categoryId: String,
    difficulty: String,
    prompt: String,
    question: String,
    options: List<String>,
    answerIndex: Int,
    explanation: String,
    sourceHint: String
): HighwayCodeQuestion {
    return HighwayCodeQuestion(
        id = "hc_%03d".format(id),
        categoryId = categoryId,
        difficulty = difficulty,
        prompt = prompt,
        question = question,
        options = options,
        answerIndex = answerIndex,
        explanation = explanation,
        sourceHint = sourceHint
    )
}
