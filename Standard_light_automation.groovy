import groovy.json.JsonOutput

#include RL.Utils
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
        input "logEnable", "bool", title: "Enable extensive logging", required: false
        input("txtEnable", "bool", title: "Enable description text logging.", defaultValue: true, required: false)
        input "doTest", "bool", title: "Use test devices", required: false
        input "testMotionTurnOn", "capability.motionSensor", title: "Test motion sensor turn ons", required: false, multiple: true
        input "testMotionKeepOn", "capability.motionSensor", title: "Test motion sensor keep ons", required: false, multiple: true
        input "testLuxSensor", "capability.illuminanceMeasurement", title: "Test lux sensor ", required: false
        input "testLightSwitches", "capability.switchLevel", title: "TEST lights", required: true, multiple: true
        input "testIsDarkSwitch", "capability.switch", title: "Test IsDark Switch", required: false
        input "testMode", "mode", title: "TEST Mode", required: true
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
            input "brightness_${mode}", "number", title: "${mode} brightness (0-100)", required: true, defaultValue: 88
            input "color_${mode}", "color", title: "${mode} color", required: false, defaultValue: "#ffffff"
            input "stayon_${mode}", "number", title: "${mode} stay-on duration seconds", required: true, defaultValue: 30
        }
    }
}

//WRAPPERS
def logdebug(message) {
    if (logEnable)
    {
        logDebug(state, message)
    }
}

def loginfo(message) {
    if(txtEnable)
    {
        logInfo(state, message)
    }
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
    logdebug "getMotionSensorTurnOn: ${getMotionSensorTurnOn()}"
    logdebug "getMotionSensorKeepOn: ${getMotionSensorKeepOn()}"
    logdebug "getLuxSensor: ${getLuxSensor()}"
    logdebug "getIsDarkSwitch: ${getIsDarkSwitch()}"
    logdebug "getLights: ${getLights()}"
    logdebug "getMode: ${getMode()}"
    
    getMotionSensorTurnOn().each { sensor ->
        subscribe(sensor, "motion.active", motionDetectedTurnOnHandler)
        subscribe(sensor, "motion.inactive", motionStoppedTurnOnHandler)
    }
    getMotionSensorKeepOn().each { sensor ->
        subscribe(sensor, "motion.active", motionDetectedKeepOnHandler)
        subscribe(sensor, "motion.inactive", motionStoppedKeepOnHandler)
    }    
    subscribe(getLuxSensor(), "illuminance", luxChangedHandler)    
        
    subscribe(location, "mode", modeChangedHandler)
    
    logdebug "subscriptions complete."
    
    unschedule(turnOffIfInactive)
}


//DEVICES
def getMotionSensorTurnOn(){
     return doTest ? testMotionTurnOn : motionSensorsTurnOn
}
def getMotionSensorKeepOn(){
     return doTest ? testMotionKeepOn : motionSensorsKeepOn
}
def getLuxSensor(){
     return doTest ? testLuxSensor : luxSensor
}
def getIsDarkSwitch(){
     return doTest ? testIsDarkSwitch : isDarkSwitch
}
def getLights(){
     return doTest ? testLightSwitches : lightSwitches
}
def getMode(){
     return doTest ? testMode : location.mode  
}
//CONFIGS
def getLux(){
    return getLuxSensor()?.currentIlluminance 
}

def isDark(){
    return getIsDarkSwitch()?.currentValue("switch") == "on"
}
def isLuxLow(){
    return getLux() < luxThreshold
}
def getConfigBrightness(){
    return settings["brightness_${getMode()}"] ?: 100  
}
def getConfigColor(){
    def color = settings["color_${getMode()}"]
    return color
}
def getStayOn(){
    def stayon = settings["stayon_${getMode()}"]
    return stayon < 1 || stayon == null ? 30 : stayon
}

def motionDetectedTurnOnHandler(evt) {
    logdebug "motionDetectedTurnOnHandler: ${evt?.displayName}"
    //logdebug "${jsonEvent(evt)}"
    unschedule(turnOffIfInactive)
    ActionLights("motionDetectedTurnOnHandler")
}

def motionDetectedKeepOnHandler(evt) {
    logdebug "motionDetectedKeepOnHandler: ${evt?.displayName}"
    logdebug "turnoff in: ${getStayOn()}, motionDetectedKeepOnHandler triggered"
    extendSchedule(getStayOn(), turnOffIfInactive)
}

def motionStoppedTurnOnHandler(evt) {
    logdebug "motionStoppedTurnOnHandler: ${evt?.displayName}"
}

def motionStoppedKeepOnHandler(evt) {
    logdebug "motionStoppedKeepOnHandler: ${evt?.displayName}"
}

def luxChangedHandler(evt) {
    def anylightson = isAnySwitchOn(getLights())
    logdebug "Lux level ${evt?.displayName} (virt: ${isVirtual(evt?.device)}) changed to ${evt.value}, any lights on: ${anylightson}"
    if(anylightson)
    {
        logdebug "some lights are on"
        if (evt?.device.currentIlluminance >= luxThreshold) {
            logdebug "${isVirtual(evt?.device) ? "virtual" : "real"} lux is now above threshold, turning off lights if no motion."
            extendSchedule(1, turnOffIfInactive)
        }           
    }
}

def modeChangedHandler(evt) {
    logdebug "modeChangedHandler: ${evt?.displayName}"

    // Reevaluate the brightness, color, and stay-on duration based on the new mode
    ActionLights("modeChangedHandler", true)
}



void ActionLights(strcaller, force = false)
{
    def comment = "---${["isDark()": isDark(), "isLuxLow": isLuxLow].findAll { it.value }.collect { "${it.key}" }.join(", ")}---"
    
    if (includedModes && includedModes.contains(getMode())) 
    {
        switch (true) 
        {
            case (isDark() || isLuxLow()):
                
                logdebug "getLights: ${getLights()}"
                def anylightson = isAnySwitchOn(getLights())
                
                if(anylightson)
                {
                    log.debug "some light are already on, do nothing"
                }
                else
                {    
                    getLights().each { light ->
                        //logdebug(light.currentValue("switch"))
                        if(!isSwitchOn(light) || force) 
                        {
                            setLight(light, turnOffIfInactive, "${strcaller}.${comment}")
                        } 
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
        loginfo "${getMode()} not in ${includedModes}, do nothing"
    }
}

def setLight(device, turnOffMethod, reason, boolean force = false) 
{
        def logMessage = "${device.displayName} on, mode ${getMode()}, ${getConfigBrightness()}%, color ${getConfigColor()}, ${getStayOn()} secs, force: ${force}, reason: ${reason}"
        loginfo logMessage    
        
        //device.on()
        logdebug "isColorDevice: ${isColorDevice(device)}, color: ${getConfigColor()}"
        
        if(isColorDevice(device))
        {
            def hsl = hexToHSL(getConfigColor())
            logdebug "hsl: ${hsl}"
            device.setColor([hue: hsl.hue, saturation: hsl.saturation, level: getConfigBrightness()])
        }
        else
        {
            device.setLevel(getConfigBrightness())        
        }
        logdebug "turnoff in: ${getStayOn()}, initially triggered"
        extendSchedule(getStayOn(), turnOffMethod)
}




def turnOffIfInactive() 
{
    def anylightson = isAnySwitchOn(getLights())
    if(isAnySwitchOn(getLights()))
    {
        def activeSensors = getMotionSensorKeepOn().findAll { it.currentMotion == "active" }
        if (activeSensors.isEmpty()) {
            loginfo "turnOffIfInactive, no active sensors, turning off lights."
            getLights().each { light ->
                light.off()
            }
        } else {
            loginfo "turnOffIfInactive, active sensors: ${activeSensors}, keeping lights on, turnoff in: ${getStayOn()}, self triggered"
            extendSchedule(getStayOn(), turnOffIfInactive)        
        }        
    }
    else
    {
        loginfo "turnOffIfInactive, no lights on, stopping"
    }

}
