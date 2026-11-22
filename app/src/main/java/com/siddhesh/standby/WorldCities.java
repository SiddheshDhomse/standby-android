package com.siddhesh.standby;

/** Cities the user can pick for the World clock widget and face. */
final class WorldCities {
    private WorldCities() {}

    static final String[] NAMES = {
            "Cupertino", "Seattle", "New York", "Toronto", "Chicago", "São Paulo", "London", "Dublin",
            "Paris", "Berlin", "Amsterdam", "Zurich", "Dubai", "Pune", "Mumbai", "Bengaluru", "Singapore",
            "Hong Kong", "Shanghai", "Tokyo", "Seoul", "Sydney", "Melbourne", "Auckland",
    };
    static final String[] ZONES = {
            "America/Los_Angeles", "America/Los_Angeles", "America/New_York", "America/Toronto",
            "America/Chicago", "America/Sao_Paulo", "Europe/London", "Europe/Dublin", "Europe/Paris",
            "Europe/Berlin", "Europe/Amsterdam", "Europe/Zurich", "Asia/Dubai", "Asia/Kolkata",
            "Asia/Kolkata", "Asia/Kolkata", "Asia/Singapore", "Asia/Hong_Kong", "Asia/Shanghai",
            "Asia/Tokyo", "Asia/Seoul", "Australia/Sydney", "Australia/Melbourne", "Pacific/Auckland",
    };
    /** Cupertino, New York, London, Tokyo */
    static final int[] DEFAULT = {0, 2, 6, 19};
    static final int MAX = 5;
}
