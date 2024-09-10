
definition(
    name: "Standard light automation",
    namespace: "Roberto Lee",
    author: "RL",
    description: "Turn lights on based on motion, lux, mode, and apply mode-specific brightness and color when lights are off.",
    category: "Convenience",
    iconUrl: "",
    iconX2Url: ""
)

preferences {
    section("Settings") {
        input name: "logPrefix", type: "text", title: "Prefix log", required: true, defaultValue: "⚙️"
        input "debugMode", "bool", title: "Enable Debug Mode", required: false
        input "doTest", "bool", title: "Activate test devices", required: false
        input "testTrigger1", "capability.switch", title: "Test trigger 1", required: false
        input "testTrigger2", "capability.motionSensor", title: "Test trigger 2", required: false
        input "testLuxSensor", "capability.illuminanceMeasurement", title: "Test lux sensor physical lux sensor when debug", required: false
    }    
    section("Select devices") {
        input "motionSensorsTurnOn", "capability.motionSensor", title: "Select motion sensors to turn on light(s)", required: true, multiple: true
        input "motionSensorsKeepOn", "capability.motionSensor", title: "Select motion sensors to keep on light(s)", required: true, multiple: true
        input "luxSensor", "capability.illuminanceMeasurement", title: "Select lux sensor", required: true
        input "lightSwitches", "capability.switchLevel", title: "Select lights", required: true, multiple: true
        input "isDarkSwitch", "capability.switch", title: "IsDark Switch", required: false
    }
    section("Configs") {
        input "luxThreshold", "number", title: "Lux Threshold for turning on the lights", required: true, defaultValue: 50
    }
    section("Select Mode(s) to Activate Turning On Light") {
        input "includedModes", "mode", title: "Modes to Include", multiple: true, required: false
    }

    section("Mode-specific settings ${includedModes}") {
        includedModes.each { mode ->
            //section("${mode}") 
            
            //if (includedModes?.contains(mode)) {  // Only display settings for selected modes
                input "brightness_${mode}", "number", title: "${mode} brightness (0-100)", required: true, defaultValue: 88
                input "color_${mode}", "color", title: "${mode} color", required: false
                input "stayon_${mode}", "number", title: "${mode} stay-on duration seconds", required: true, defaultValue: 30
            //}
            
        }
    }

}

//WRAPPERS
def logdebug(message) {
    if (debugMode)
    {
        logDebug(state, message)
    }
    
}

def loginfo(message) {
    logInfo(state, message)
}


def installed() {
    logdebug "Installed with settings: ${settings}"
    initialize()
}

def updated() {
    logdebug "Updated with settings: ${settings}"
    unsubscribe()
    initialize()
}

def initialize() {
    state.logPrefix = "${settings.logPrefix}"
    logdebug "debugMode = ${debugMode}"    
    logdebug "doTest = ${doTest}"    

    logdebug "initializing..."
    
    // Subscribe to motion sensors
    motionSensorsTurnOn.each { sensor ->
        subscribe(sensor, "motion.active", motionDetectedTurnOnHandler)
        subscribe(sensor, "motion.inactive", motionStoppedTurnOnHandler)
    }
    motionSensorsKeepOn.each { sensor ->
        subscribe(sensor, "motion.active", motionDetectedKeepOnHandler)
        subscribe(sensor, "motion.inactive", motionStoppedKeepOnHandler)
    }
    
    // Subscribe to lux sensor and mode changes
    subscribe(luxSensor, "illuminance", luxChangedHandler)
    subscribe(location, "mode", modeChangedHandler)
    
    if(doTest)
    {
        if (testTrigger1) {
            logdebug "testTrigger1 available"
            subscribe(testTrigger1, "switch.on", testTrigger1Handler)
        }   
        if (testTrigger2) {
            logdebug "testTrigger2 available"
            subscribe(testTrigger2, "switch.on", testTrigger2Handler)
        }         
        if (testLuxSensor) {
            logdebug "testLuxSensor available"
            subscribe(testLuxSensor, "illuminance", luxChangedHandler)
        }     
    }
    logdebug "subscriptions complete."
}

def testTrigger1Handler(evt) {
    logdebug "testTrigger1Handler: ${evt.value}"
    motionDetectedTurnOnHandler("test")
}

def testTrigger2Handler(evt) {
    logdebug "testTrigger2Handler: ${evt.value}"
    //motionDetectedTurnOnHandler(null)
}

def motionDetectedTurnOnHandler(evt) {
    logdebug "motionDetectedTurnOnHandler: ${evt}"
    unschedule(turnOffIfInactive)
    ActionLights("motionDetectedTurnOnHandler")
}

def motionStoppedTurnOnHandler(evt) {
    logdebug "motionStoppedTurnOnHandler: ${evt}"
}

def motionDetectedKeepOnHandler(evt) {
    logdebug "motionDetectedKeepOnHandler: ${evt}"
    logdebug "turnoff in: ${state.stayon}, motionDetectedKeepOnHandler triggered"
    extendSchedule(state.stayon, turnOffIfInactive)
}

def motionStoppedKeepOnHandler(evt) {
    logdebug "motionStoppedKeepOnHandler: ${evt}"
}



def luxChangedHandler(evt) {
    def lightson = isAnySwitchOn(lightSwitches)
    logdebug "Lux level ${evt?.displayName} (virt: ${isVirtual(evt?.device)}) changed to ${evt.value}, any lights on ${lightson}"
    if(lightson)
    {
        logdebug "some lights are on"
        if (evt?.device.currentIlluminance >= luxThreshold) {
            logdebug "${isVirtual(evt?.device) ? "virtual" : "real"} lux is now above threshold, turning off lights if no motion."
            extendSchedule(1, turnOffIfInactive)
        }           
        
        /*
        if (doTest)
        {
            if (testLuxSensor.currentIlluminance >= luxThreshold) {
                logdebug "virtual lux is now above threshold, turning off lights if no motion."
                extendSchedule(1, turnOffIfInactive)
            }         
        }
        else
        {
            if (luxSensor.currentIlluminance >= luxThreshold) {
                logdebug "real lux is now above threshold, turning off lights if no motion."
                extendSchedule(1, turnOffIfInactive)
            }    
        }
        */
    }

    
    /*
    // Reevaluate if lights should stay on or turn off based on the new lux level
    if (luxSensor.currentIlluminance >= luxThreshold) {
        logdebug "Lux is now above threshold, turning off lights if no motion."
        extendSchedule(1, turnOffIfInactive)
    } else {
        
        log.debug "Lux is below threshold, rechecking motion sensors."
        def activeSensors = motionSensors.findAll { it.currentMotion == "active" }
        if (!activeSensors.isEmpty()) {
            ActionLights()
        }
    }
    */
}

def modeChangedHandler(evt) {
    logdebug "modeChangedHandler: ${evt?.value}"

    // Reevaluate the brightness, color, and stay-on duration based on the new mode
    ActionLights("modeChangedHandler", true)
}


def ActionLights(strcaller, force = false)
{
    def luxDevice = debugMode ? testLuxSensor : luxSensor
    def mode = location.mode
    def isDarkOn = isDarkSwitch?.currentValue("switch") == "on"
    def isLuxLow = luxDevice.currentIlluminance < luxThreshold

    if (doTest)
    {
        //isDarkOn = false;
        //mode = "Normal"
        //mode = "SleepAll"
        //mode = "SleepSome"
        //mode = "Away"
    }

    def brightness = settings["brightness_${mode}"] ?: 100  // Default brightness to 100 if not set
    def color = settings["color_${mode}"]  // Color for the current mode
    def stayon = settings["stayon_${mode}"]  // Color for the current mode
    state.stayon = stayon
    //logdebug "stayon ${state.stayon}"
    
    def comment = "---${["isDarkOn": isDarkOn, "isLuxLow": isLuxLow].findAll { it.value }.collect { "${it.key}" }.join(", ")}---"
    
    if (includedModes && includedModes.contains(mode)) 
    {
        if(isLuxLow)
        {
            logdebug "${luxDevice.currentIlluminance}(lux) < ${luxThreshold} (threshold)"
        }

        switch (true) 
        {
            case (isDarkOn || isLuxLow):
                lightSwitches.each { light ->
                    //if (light.currentSwitch == "off" || force) 
                    if(isSwitchOn(light) || force) 
                    {
                        setLight(light, mode, brightness, color, stayon, turnOffIfInactive, "${strcaller}.${comment}")
                    } else {
                        log.debug "Light is already on, not changing brightness or color."
                    }
                }            
                break
            default:
                loginfo "no matching condition, do nothing"
            break
        } 
    }
    else
    {
        loginfo "${mode} not in ${includedModes}, do nothing"
    }
}

def setLight(device, mode, brightness, color, stayon, turnOffMethod, reason, boolean force = false) 
{
        def logMessage = "${device.displayName} on, mode ${mode}, ${brightness}%, color ${color}, ${stayon} secs, force: ${force}, reason: ${reason}"
        loginfo logMessage    
        
        if (!doTest) 
        {
            device.setLevel(brightness)
            
          
            /*
            if(color)
            {
                device.setColor(hexToHSL(color))
            }

            if (colorLights?.contains(light) && color) {
                log.debug "Applying color: ${color} for mode: ${currentMode}"
                light.setColor(color)
            }
            */
        }
        logdebug "turnoff in: ${stayon}, initially triggered"
        extendSchedule(stayon, turnOffMethod)
}




def turnOffIfInactive() {
    // Check if all motion sensors are inactive
    def activeSensors = motionSensorsKeepOn.findAll { it.currentMotion == "active" }
    logdebug "Active sensors: ${activeSensors}"
    if (activeSensors.isEmpty()) {
        loginfo "No motion detected from any sensor, turning off lights."
        lightSwitches.each { light ->
            if (!doTest)
            {
                light.off()
            }
        }
    } else {
        logdebug "Motion detected by other sensors, keeping lights on."
        logdebug "turnoff in: ${state.stayon}, self triggered"
        extendSchedule(state.stayon, turnOffIfInactive)        
    }
}

// ~~~~~ start include (4) RL.Utils ~~~~~
library ( // library marker RL.Utils, line 1
 author: "Roberto Lee", // library marker RL.Utils, line 2
 category: "utils", // library marker RL.Utils, line 3
 description: "Some description", // library marker RL.Utils, line 4
 name: "Utils", // library marker RL.Utils, line 5
 namespace: "RL", // library marker RL.Utils, line 6
 documentationLink: "" // library marker RL.Utils, line 7
) // library marker RL.Utils, line 8

/* // library marker RL.Utils, line 10
//#include RL.Utils // library marker RL.Utils, line 11
//WRAPPERS // library marker RL.Utils, line 12
def logdebug(message) { // library marker RL.Utils, line 13
    logDebug(state, message) // library marker RL.Utils, line 14
} // library marker RL.Utils, line 15

def loginfo(message) { // library marker RL.Utils, line 17
    logInfo(state, message) // library marker RL.Utils, line 18
} // library marker RL.Utils, line 19

*/ // library marker RL.Utils, line 21

void logDebug(def state, String message)  // library marker RL.Utils, line 23
{ // library marker RL.Utils, line 24
    log.debug "[${state.logPrefix}] ${message}" // library marker RL.Utils, line 25
} // library marker RL.Utils, line 26


void logInfo(def state, String message)  // library marker RL.Utils, line 29
{ // library marker RL.Utils, line 30
    log.info "[${state.logPrefix}] ${message}" // library marker RL.Utils, line 31
} // library marker RL.Utils, line 32

def extendSchedule(duration, method) // library marker RL.Utils, line 34
{ // library marker RL.Utils, line 35
    // Cancel any previously scheduled turn-off method // library marker RL.Utils, line 36
    unschedule(method) // library marker RL.Utils, line 37

    // Schedule turning off the light after the specified duration // library marker RL.Utils, line 39
    runIn(duration, method)  // library marker RL.Utils, line 40
} // library marker RL.Utils, line 41

def isVirtual(device) { // library marker RL.Utils, line 43
    // Convert type name to lowercase and check if it contains "virtual" // library marker RL.Utils, line 44
    return device?.getTypeName()?.toLowerCase()?.contains("virtual") ?: false // library marker RL.Utils, line 45
} // library marker RL.Utils, line 46

def isSwitchOn(device) { // library marker RL.Utils, line 48
    // Check if the light is on // library marker RL.Utils, line 49
    return device.currentValue("switch") == "on" // library marker RL.Utils, line 50
    //return device.currentSwitch == "on" // library marker RL.Utils, line 51
} // library marker RL.Utils, line 52

def isAnySwitchOn(devices)  // library marker RL.Utils, line 54
{ // library marker RL.Utils, line 55
    def isOn = false // library marker RL.Utils, line 56
    devices.each { d -> // library marker RL.Utils, line 57
        if (d.currentSwitch == "on")  // library marker RL.Utils, line 58
        { // library marker RL.Utils, line 59
            isOn = true // library marker RL.Utils, line 60
        } // library marker RL.Utils, line 61
    } // library marker RL.Utils, line 62
    return isOn // library marker RL.Utils, line 63
} // library marker RL.Utils, line 64


def isDinnerTime(){ // library marker RL.Utils, line 67
    //return isTimeBetween("12:00", "16:00"); // library marker RL.Utils, line 68
    return isTimeBetween("18:00", "19:00"); // library marker RL.Utils, line 69
} // library marker RL.Utils, line 70


def isTimeBetween(timeStart, timeEnd) { // library marker RL.Utils, line 73
    def now = new Date() // library marker RL.Utils, line 74
    def start = parseTime(timeStart) // library marker RL.Utils, line 75
    def end = parseTime(timeEnd) // library marker RL.Utils, line 76

    // Adjust end time if it is before the start time (for times that cross midnight) // library marker RL.Utils, line 78
    if (end < start) { // library marker RL.Utils, line 79
        end += 24 * 60 * 60 * 1000 // add a day in milliseconds // library marker RL.Utils, line 80
    } // library marker RL.Utils, line 81

    return now >= start && now <= end // library marker RL.Utils, line 83
} // library marker RL.Utils, line 84

def parseTime(timeStr) { // library marker RL.Utils, line 86
    def timeParts = timeStr.split(':') // library marker RL.Utils, line 87
    def now = new Date() // library marker RL.Utils, line 88
    return new Date(now.year, now.month, now.date, timeParts[0].toInteger(), timeParts[1].toInteger()) // library marker RL.Utils, line 89
} // library marker RL.Utils, line 90


def hexToHSL(hex) { // library marker RL.Utils, line 93
    // Remove the # symbol if present // library marker RL.Utils, line 94
    hex = hex.replace("#", "") // library marker RL.Utils, line 95

    // Convert hex to RGB // library marker RL.Utils, line 97
    int r = Integer.valueOf(hex.substring(0, 2), 16) // library marker RL.Utils, line 98
    int g = Integer.valueOf(hex.substring(2, 4), 16) // library marker RL.Utils, line 99
    int b = Integer.valueOf(hex.substring(4, 6), 16) // library marker RL.Utils, line 100

    float rPct = r / 255.0 // library marker RL.Utils, line 102
    float gPct = g / 255.0 // library marker RL.Utils, line 103
    float bPct = b / 255.0 // library marker RL.Utils, line 104

    float max = Math.max(rPct, Math.max(gPct, bPct)) // library marker RL.Utils, line 106
    float min = Math.min(rPct, Math.min(gPct, bPct)) // library marker RL.Utils, line 107

    float h = 0 // library marker RL.Utils, line 109
    float s = 0 // library marker RL.Utils, line 110
    float l = (max + min) / 2 // library marker RL.Utils, line 111

    if (max != min) { // library marker RL.Utils, line 113
        float d = max - min // library marker RL.Utils, line 114
        s = (l > 0.5) ? d / (2.0 - max - min) : d / (max + min) // library marker RL.Utils, line 115

        if (max == rPct) { // library marker RL.Utils, line 117
            h = (gPct - bPct) / d + (gPct < bPct ? 6 : 0) // library marker RL.Utils, line 118
        } else if (max == gPct) { // library marker RL.Utils, line 119
            h = (bPct - rPct) / d + 2 // library marker RL.Utils, line 120
        } else if (max == bPct) { // library marker RL.Utils, line 121
            h = (rPct - gPct) / d + 4 // library marker RL.Utils, line 122
        } // library marker RL.Utils, line 123

        h /= 6 // library marker RL.Utils, line 125
    } // library marker RL.Utils, line 126

    h = Math.round(h * 100) // library marker RL.Utils, line 128
    s = Math.round(s * 100) // library marker RL.Utils, line 129
    l = Math.round(l * 100) // library marker RL.Utils, line 130

    return [hue: h, saturation: s, level: l] // library marker RL.Utils, line 132
} // library marker RL.Utils, line 133




// ~~~~~ end include (4) RL.Utils ~~~~~
