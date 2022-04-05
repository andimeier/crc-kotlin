package com.siemens.bmd.configapp

data class Position(
    val label: String, // the textual representation used in the user interface user
    val value: Int, // the numeric representation of the position stored in the config
    val radioChannel1: Int,
    val slot: Int,
    val radioChannel2: Int? = null,
    val linkDirection2: Int? = null,
    val radioChannel3: Int? = null,
    val linkDirection3: Int? = null,
    val orientation: Int,
    val networkId: Int,
)

data class Car(
    val label: String,
    val value: Int,
    val positions: Position[],
)

data class Project(
    val label: String, 
    val value: Int, 
    val description: String,
)

data class ProjectConfig(
    val project: Project, 
    val trains: Int[], 
    val cars: Car[],
) {
    /**
     * Find the position config for the specified position.
     * 
     * If the position could not be found, null is returned. // FIXME nicht Exception? Sollte ja eigentlich gar nicht vorkommen koennen ...?
     * 
     * @param car the car label
     * @param position the position label
     * @return the position config object
     */
    fun find(car: String, position: String) : Position? {
        return cars.firstOrNull { it.label == car }?.positions?.firstOrNull { it.label }
    }
}


/**
 * Read project config from a YAML config file.
 * 
 * @param path the path to the YAML config file
 * @return the parsed project config object
 */
fun loadFromFile(path: Path): ProjectConfig {
    val mapper = ObjectMapper(YAMLFactory()) // enable YAML parsing
    mapper.registerModule(KotlinModule()) // enable Kotlin support

    return Files.newBufferedReader(path).use {
        mapper.readValue(it, ProjectConfig::class.java)
    }
}

/**
 * Populate project config with hardcoded test data.
 * 
 * @return the populated project config object
 */
fun populate(): ProjectConfig {
    return ProjectConfig(
        project=Project(
            label="NextGenRRX",
            value=1,
            description="NextGen RRX Zug 83",
        ),
        trains=listOf(95, 96),
        cars=listOf<Car>(
        Car(
            label="EWB", 
            value=1, 
            positions=listOf<Position>(
                Position("A1L", 1, radioChannel1=1, slot=1, orientation=1, networkId=1),
                Position("A1R", 2, radioChannel1=1, slot=2, orientation=1, networkId=1),
                Position("A2L", 3, radioChannel1=1, slot=3, orientation=2, networkId=1),
                Position("A2R", 4, radioChannel1=1, slot=4, orientation=1, networkId=1),
                Position("A3L", 1, radioChannel1=2, slot=1, orientation=1, networkId=2),
                Position("A3R", 2, radioChannel1=2, slot=2, orientation=1, networkId=2),
                Position("A4L", 3, radioChannel1=2, slot=3, orientation=2, networkId=2),
                Position("A4R", 4, radioChannel1=2, slot=4, orientation=1, networkId=2),
            )            
        ),
        Car(
            label="MWD", 
            value=2, 
            positions=listOf<Position>(
                Position("A1L", 1, radioChannel1=3, slot=1, orientation=1, networkId=3),
                Position("A1R", 2, radioChannel1=3, slot=2, orientation=1, networkId=3),
                Position("A2L", 3, radioChannel1=3, slot=3, orientation=2, networkId=3),
                Position("A2R", 4, radioChannel1=3, slot=4, orientation=1, networkId=3),
                Position("A3L", 1, radioChannel1=4, slot=1, orientation=1, networkId=4),
                Position("A3R", 2, radioChannel1=4, slot=2, orientation=1, networkId=4),
                Position("A4L", 3, radioChannel1=4, slot=3, orientation=2, networkId=4),
                Position("A4R", 4, radioChannel1=4, slot=4, orientation=1, networkId=4),
            )            
        ),
    )
}

fun find