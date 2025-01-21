import groovy.json.JsonOutput

definition(
    name: "🚀Lights automation configurable v3",
    namespace: "Roberto Lee",
    author: "RL",
    description: "Turn lights on based on motion, lux, mode, and apply mode-specific brightness and color when lights are off. Doors status applies",
    category: "Convenience",
    iconUrl: "",
    iconX2Url: ""
)

preferences {
    section("Settings")
    {
        input "aboutRoom", "enum", title: "Select room", options: ["h", "lr", "mb", "g", "k"], required: true
        //input name: "logPrefix", type: "text", title: "Prefix log", required: true, defaultValue: "⚙️"
        input "logEnable", "bool", title: "Enable extensive logging", required: true
        input "txtEnable", "bool", title: "Enable description text logging.", defaultValue: true, required: true
    }
    section("Select devices") {
        input "motionSensorsTurnOn", "capability.motionSensor", title: "Select motion sensors to turn on light(s)", required: true, multiple: true
        input "motionSensorsKeepOn", "capability.motionSensor", title: "Select motion sensors to keep on light(s)", required: true, multiple: true
        input "luxSensor", "capability.illuminanceMeasurement", title: "Select lux sensor", required: false
        input "socketPlug", "capability.powerMeter", title: "Select socket plug, power in watts", required: false
        lightGroupArray().each { grp ->
            def grpName = grp[0]
            def gprDescription = grp[1].toLowerCase()
            input "${grpName}", "capability.switchLevel", title: "Select ${gprDescription}", required: false, multiple: true
        }
        input "isDarkSwitch", "capability.switch", title: "IsDark Switch", required: false
        input "roomDoors", "capability.contactSensor", title: "Room doors", required: false, multiple: true
        input "houseDoors", "capability.contactSensor", title: "House doors", required: false, multiple: true
        input "turnOnButtons", "capability.pushableButton", title: "Which buttons turnon?", required: false, multiple: true
    }
    section("Configs") {
        input "luxThreshold", "number", title: "Lux Threshold for turning on the lights", required: false, defaultValue: 50
        input "powerThreshold", "number", title: "Power threshold for it to be considered on (watts)", required: false, defaultValue: 55
    }
    section("Select Mode(s) to Activate Turning On Light") {
        input "includedModes", "mode", title: "Modes to Include", multiple: true, required: false
    }
    includedModes.each { mode ->
        lightGroupArray().each { grp ->  // Loop through each row
            def grpName = grp[0]
            logdebug "${grpName}"
            stateGroupArray().each { state ->
                def stateName = state[0]
                def nms = getConfigNames(mode, grpName, stateName)
                def doIt = getConfigDo(nms)
                section("${nms.title} <code style='background-color: yellow'>${nms.base}</code>", hideable: true, hidden: !doIt) {
                    input "${nms.do}", "bool", title: "${nms.statedescription} DO? Otherwise skip actions <code style='background-color: yellow'>${nms.do}</code>", required: true, defaultValue: false
                    input "${nms.turnon}", "bool", title: "${nms.statedescription} turn light on? <code style='background-color: yellow'>${nms.turnon}</code>", required: true, defaultValue: false
                    input "${nms.stayon}", "number", title: "${nms.statedescription} stayon in seconds <code style='background-color: yellow'>${nms.stayon}</code>", required: true, defaultValue: 30
                    input "${nms.brightness}", "number", title: "${nms.statedescription} brightness (0-100)<code style='background-color: yellow'>${nms.brightness}</code>", required: true, defaultValue: 88
                    input "${nms.color}", "color", title: "${nms.statedescription} color <code style='background-color: yellow'>${nms.color}</code>", required: false, defaultValue: "#ffffff"
                }
            }
        }
    }
}
def getMode()
{
    def chosenMode = location.mode 
    //chosenMode = "SleepAll" 
    return chosenMode
}

def lightGroupArray()
{
    return [
        ["mainlights", "Main lights"],
        ["sidelights", "Side lights"],
      
    ]
} 
def stateGroupArray(){
    return [
        ["no", "motion not dark"],
        ["dark", "motion dark"],
        ["buttonpushed", "when button pushed"],
        ["buttondoublepushed", "when button double pushed"],
        ["roomdoorsopen", "when room door open"],
        ["housedoorsopen", "when house door open"],
        ["istvon", "when tv on"],
    ]
} 

def determineState(button = 0)
{
    //logdebug "[${allStates().collect { "${it.key} : ${it.value}" }.join("] [")}]"
    def isLowLight = isDark() || isLuxLow()
    logdebug("isDark: ${isDark()}, isLuxLow ${isLuxLow()}", "determineState")
    logdebug("button: ${button}", "determineState")
    def result = "noMatch"
    if(aboutRoom == "h")
    {
        switch (true) 
        {
            case (button == 1):
                result = stateGroupArray()[2][0] //BUTTON PUSHED
                break
            case (button == 2):
                result = stateGroupArray()[3][0] //BUTTON PUSHED
                break
            case (isAnyContactOn(roomDoors) && (getMode() == "SleepAll")):
                result = stateGroupArray()[4][0]
                break
            case isAnyContactOn(houseDoors) && isEarlyGoToWorkTimeOn() && (getMode() == "SleepSome"):
                result = stateGroupArray()[5][0]
                break
            case isAnyContactOn(houseDoors) && (getMode() == "Normal"):

                result = stateGroupArray()[5][0]

                break
            case (isAnyMotionActive(motionSensorsKeepOn) && (isLowLight || ["SleepAll", "SleepSome"].contains(getMode())) ):
                result = stateGroupArray()[1][0] 
                break
            case (isAnyMotionActive(motionSensorsKeepOn) && !isLowLight):
                result = stateGroupArray()[0][0] 
                break
            default:
                //result = "noMatch"
                result = stateGroupArray()[1][0] 
                logdebug ("determineState NO MATCH", "determineState")
                break
        } 
    }
    else if(aboutRoom == "lr")
    {
        switch (true) 
        {
            // case (isDinnerTimeOn()):
            //     result = stateGroupArray()[7][0] 
            //     break
            case (isSocketPlugUsingPower() && isLowLight)://TV
                result = stateGroupArray()[7][0] 
                break
            case (isAnyMotionActive(motionSensorsKeepOn) && isLowLight):
                result = stateGroupArray()[1][0] 
                break
            case (isAnyMotionActive(motionSensorsKeepOn) && !isLowLight):
                result = stateGroupArray()[0][0] 
                break
            default:
                //result = "noMatch"
                result = stateGroupArray()[1][0] 
                logdebug ("determineState NO MATCH", "determineState")
            break
        }
    }
    else
    {
        switch (true)
        {
            case (isAnyMotionActive(motionSensorsKeepOn) && isLowLight):
                result = stateGroupArray()[1][0] 
                break
            case (isAnyMotionActive(motionSensorsKeepOn) && !isLowLight):
                result = stateGroupArray()[0][0] 
                break
            default:
                result = stateGroupArray()[1][0] 
                logdebug ("determineState NO MATCH", "determineState")
            break
        }
    } 
    //logdebug ("STATE: ${result}", "determineState")
    return result
}

def logdebug(message, methodName ="") {
    if (logEnable)
    {
        if(methodName != "")
        {
            state.logPostfix = methodName
        }
        log.debug "[⚙️${state.logPrefix}] ${message} ➜${state.logPostfix}"
    }
}

def loginfo(message, methodName ="") {
    if(txtEnable)
    {
        if(methodName != "")
        {
            state.logPostfix = methodName
        }
        log.info "[⚙️${state.logPrefix}] ${message} ➜${state.logPostfix}"
    }
}

def installed() {
    //logdebug "Installed with settings: ${settings}"
    initialize()
}

def updated() {
    //logdebug "Updated with settings: ${settings}"
    unsubscribe()
    initialize()
}

def initialize() {
    state.logPrefix = "${settings.aboutRoom}"
    logdebug("initializing...", "initialize")

    subscribe(location, "mode", modeChangedHandler)

    logdebug "getIsDarkSwitch: ${isDarkSwitch} = ${isDark()}"
    subscribe(isDarkSwitch, "switch.on", switchesHandler)
    subscribe(isDarkSwitch, "switch.off", switchesHandler)

    if(luxSensor)
    {
        logdebug "getLuxSensor: ${luxSensor}, islow = ${isLuxLow()}"
        subscribe(luxSensor, "illuminance", luxChangedHandler)
    }

    if(motionSensorsTurnOn)
    {
        logdebug "getMotionSensorTurnOn: ${motionSensorsTurnOn}"
        motionSensorsTurnOn.each { sensor ->
            subscribe(sensor, "motion.active", motionDetectedTurnOnHandler)
            subscribe(sensor, "motion.inactive", motionStoppedTurnOnHandler)
        }
    }
    if(motionSensorsKeepOn)
    {
        logdebug "getMotionSensorKeepOn: ${motionSensorsKeepOn}"
        motionSensorsKeepOn.each { sensor ->
            subscribe(sensor, "motion.active", motionDetectedKeepOnHandler)
            subscribe(sensor, "motion.inactive", motionStoppedKeepOnHandler)
        }
    }

    logdebug "getMainLights: ${mainlights}"
    if(sidelights)
    {
        logdebug "getSideLights: ${sidelights}"
    }

    if(roomDoors)
    {
        logdebug "getRoomDoors: ${roomDoors}, anyopen = ${isAnyRoomDoorsOpen()}"
        subscribe(roomDoors, "contact", roomDoorsHandler)
    }
    if(houseDoors)
    {
        logdebug "getHouseDoors: ${houseDoors}, anyopen = ${isAnyHouseDoorsOpen()}"
        subscribe(houseDoors, "contact.open", houseDoorsHandler)
    }

    if(socketPlug)
    {
        logdebug "getSocketPlug: ${socketPlug}"
        subscribe(socketPlug, "power", powerChangedHandler)
    }

    if(turnOnButtons)
    {
        logdebug "getTurnOnButtons: ${turnOnButtons}"
        subscribe(turnOnButtons, "pushed", buttonTurnOnPushedHandler)
        subscribe(turnOnButtons, "doubleTapped", buttonTurnOnDoubleTappedHandler)
        subscribe(turnOnButtons, "held", buttonTurnOnHeldHandler)
    }
    logdebug "subscriptions complete."

    unschedule(turnOffIfInactive)
}

def isAnyRoomDoorsOpen(){
    return isAnyContactOn(roomDoors)
}
def isAnyHouseDoorsOpen(){
    return isAnyContactOn(houseDoors)
}

def isEarlyGoToWorkTimeOn(){
    return doTest ? testIsEarlyGoToWorkTimeSwitch?.currentValue("switch") == "on" : isEarlyGoToWorkTime()
}

def isDinnerTimeOn(){
    return doTest ? testIsDinnerTimeSwitch?.currentValue("switch") == "on" : isDinnerTime()
}
def isSocketPlugUsingPower(){
    return socketPlug?.currentValue("power") > powerThreshold
}

//CONFIGS
def getLux(){
    return luxSensor?.currentIlluminance 
}
def isDark(){
    return isDarkSwitch?.currentValue("switch") == "on"
}
def isLuxLow(){
    return getLux() < luxThreshold
}
//EVENTS HANDLERS
def motionDetectedTurnOnHandler(evt) 
{
    logdebug("motion turn on ${evt?.displayName}","motionDetectedTurnOnHandler")
    unschedule(turnOffIfInactive)
    ActionLights("motionDetectedTurnOnHandler")
}
def motionDetectedKeepOnHandler(evt) 
{
    logdebug("motion keep on ${evt?.displayName}","motionDetectedKeepOnHandler")
    //extendSchedule(1, turnOffIfInactive)
}
def motionStoppedTurnOnHandler(evt) 
{
    logdebug("motion turn on stopped ${evt?.displayName}","motionStoppedTurnOnHandler")
}

def motionStoppedKeepOnHandler(evt) 
{
    //def cfg = getConfigNames(getMode(),"mainlights", determineState())
    logdebug("motion keep on stopped ${evt?.displayName} cfg: ${cfg}","motionStoppedKeepOnHandler")
    extendSchedule(getStayOnState(), turnOffIfInactive, "motionStoppedKeepOnHandler")
}

def powerChangedHandler(evt)
{
    def currentPower = evt.value as Double  // Current power usage in watts
    def lastPower = state.lastPower ?: 0    // Previous power usage stored in state
    // Check if the change exceeds 30 watts
    def powerDifference = Math.abs(currentPower - lastPower)

    if (powerDifference > 30) 
    {
        logdebug ("powerChanged: ${evt.value}","powerChangedHandler")
        logdebug "Power change detected: current = ${currentPower}W, previous = ${lastPower}W, difference = ${powerDifference}W, threshold = ${powerThreshold}"
        logdebug "Power change exceeds 30W, taking action"
        unschedule(turnOffIfInactive)
        ActionLights("powerChangedHandler")
    }
}

def luxChangedHandler(evt) 
{
    def anylightson = isAnySwitchOn(mainlights)
    logdebug ("lux level ${evt?.displayName} (virt: ${isVirtual(evt?.device)}) changed to ${evt.value}, threshold is ${luxThreshold}, any lights on: ${anylightson}", "luxChangedHandler")
    if(anylightson)
    {
        logdebug "some lights are on"
        if (evt?.device.currentIlluminance >= luxThreshold) {
            logdebug "${isVirtual(evt?.device) ? "virtual" : "real"} lux is now above threshold, turning off lights if no motion."
            extendSchedule(getStayOnState(), turnOffIfInactive)
        }
    }
}

def switchesHandler(evt)
{
    def anylightson = isAnySwitchOn(mainlights)
    logdebug ("switch change ${evt?.displayName} (virt: ${isVirtual(evt?.device)}) changed to ${evt.value}, any lights on: ${anylightson}", "switchesHandler")
    if(anylightson)
    {
        logdebug "${isVirtual(evt?.device) ? "virtual" : "real"} ${evt?.displayName}, some lights are on, turning off lights if no motion."
        extendSchedule(getStayOnState(), turnOffIfInactive)
    }
}


def modeChangedHandler(evt) {
    logdebug("mode to ${evt.displayName}", "modeChangedHandler")
}

def roomDoorsHandler(evt) {
    if(isContactOn(evt.device))
    {
        logdebug("${evt.device} IS OPEN", "roomDoorsHandler")
    }
    else
    {
        logdebug("${evt.device} IS DICHT", "roomDoorsHandler")
    }
    setStateForced()
    ActionLights("room ${evt?.value} door change", getStateForced())
}

def houseDoorsHandler(evt) {
    if(isContactOn(evt.device))
    {
        logdebug("${evt.device} IS OPEN ${evt?.value}","houseDoorsHandler")
    }
    else
    {
        logdebug("${evt.device} IS DICHT ${evt?.value}","houseDoorsHandler")
    }
    setStateForced()
    ActionLights("house ${evt?.value} door change", getStateForced())
}


def buttonTurnOnPushedHandler(evt) {
    logdebug("button ${evt?.displayName} ${evt?.value}", "buttonTurnOnPushedHandler")
    setStateForced()
    ActionLights("buttonTurnOnPushedHandler", getStateForced())
}

def buttonTurnOnDoubleTappedHandler(evt) {
    logdebug("button ${evt?.displayName} ${evt?.value}", "buttonTurnOnDoubleTappedHandler")
    setStateForced()
    ActionLights("buttonTurnOnDoubleTappedHandler", getStateForced())
}

def buttonTurnOnHeldHandler(evt) {
    logdebug("button ${evt?.displayName} ${evt?.value}", "buttonTurnOnHeldHandler")
    setStateForced()
    ActionLights("buttonTurnOnHeldHandler", getStateForced())
}

def allStates()
{
    return ["mode":getMode(),
            "dark":isDark(),
            "lux low":isLuxLow(),
            "room door":isAnyContactOn(roomDoors),
            "house door":isAnyContactOn(houseDoors),
            "motion":isAnyMotionActive(motionSensorsKeepOn),
            "luxThreshold":luxThreshold,
            "isSocketPlugUsingPower":isSocketPlugUsingPower()
            ]
}



//CUSTOMIZE WORKINGS
void ActionLights(strcaller, forced = false)
{
    def m ="ActionLights"
    def mode = getMode()
    if (includedModes && includedModes.contains(mode)) 
    {
        def buttonPressedId = (strcaller == "buttonTurnOnPushedHandler") ? 1 : (strcaller == "buttonTurnOnDoubleTappedHandler") ? 2 : (strcaller == "buttonTurnOnHeldHandler") ? 3: 0
        def states = allStates()
        def dState = determineState(buttonPressedId)
        states["determinedstate"] = dState
        logdebug("states: ${states}", m)
        //logdebug ("[${states.collect { "${it.key}" }.join("] [")}]", "ActionLights")
        //logdebug ("[${states.findAll { it.value }.collect { "${it.key}" }.join("] [")}]", "ActionLights")

        if(dState != "noMatch")
        {
            //LOOP THIS?
            def cfgMain = getConfigNames(mode, "mainlights", dState)
            ActionLightsOneGroup(cfgMain, mainlights, turnOffIfInactiveMain, strcaller, comment, forced)
            if(sidelights)
            {
                def cfgSide = getConfigNames(mode, "sidelights", dState)
                ActionLightsOneGroup(cfgSide, sidelights, turnOffIfInactiveSide, strcaller, comment, forced)
            }
        }
        else
        {
            turnOffIfInactive()
        }
    }
    else
    {
        loginfo ("${mode} not in ${includedModes}, do nothing", m)
    }
}


def ActionLightsOneGroup(configs, lights, methodOff, strcaller, comment = "", forced = false)
{
    def m ="ActionLightsOneGroup"
    if(determinedstate != "-1")
    {
        if(lights)
        {
            //def configs = getConfigNames(mode, configsidentifier, determinedstate)
            def doLights = getConfigDo(configs)
            def doTurnOn = getConfigDoTurnOn(configs)
            def stayOn = getConfigStayOn(configs)
            def configsidentifier = configs.lightname


            if(doLights || !doTurnOn)
            {
                //logdebug ("${configsidentifier.toUpperCase()} actions", m)
                if(doTurnOn)
                {
                    def anyLightsOn = isAnySwitchOn(lights)
                    if(anyLightsOn && !forced)
                    {
                        extendSchedule(stayOn, methodOff, "${configsidentifier} already on, extend only")
                    }
                    else
                    {
                        lights.each { light ->
                            if(!isSwitchOn(light) || forced) 
                            {
                                turnOnLight(configs, light, methodOff, "${strcaller}.${comment}")
                            } 
                        }
                    }
                }
                else
                {
                    //extendSchedule(1, methodOff, "${configsidentifier} 1.turning off immediately ")
                    turnOffLights(lights, "${configsidentifier} turn off requested")
                }
            }
            else
            {
                //extendSchedule(1, methodOff, "${configsidentifier} 2.turning off immediately")
                //turnOffLights(lights, "${configsidentifier} 2.turning off immediately")
                loginfo ("${configsidentifier} DO is off, leave alone", m)
            }
        }
        else
        {
            loginfo ("${configsidentifier} no lights defined", m)
        }
    }
}

def turnOnLight(configs, device, methodOff, reason, forced = false) 
{
    if(forced)
    {
        logdebug "FORCED ON"
    }
    if(getConfigDoTurnOn(configs) || forced)
    {
        def deviceOnWith = ["brightness":"${getConfigBrightness(configs)}%",
                            "color": "${getConfigColor(configs)}",
                            "stayon":"${getConfigStayOn(configs)}s",
                            "forced":"${forced}",
                            "mode":"${getMode()}",
                            "reason":"${reason}"]

        //logdebug "cfg: ${configs}"
        if(isColorDevice(device))
        {
            def hsl = hexToHSL(getConfigColor(configs))
            logdebug "hsl: ${hsl}"
            device.setColor([hue: hsl.hue, saturation: hsl.saturation, level: getConfigBrightness(configs)])
        }
        else
        {
            device.setLevel(getConfigBrightness(configs))
        }
        //logdebug "[${allStates().collect { "${it.key} : ${it.value}" }.join("] [")}]"
        loginfo "ON ${device.displayName} [${deviceOnWith.collect { "${it.key} : ${it.value}" }.join("] [")}]"

        setStayOnState(getConfigStayOn(configs))
        extendSchedule(getStayOnState(), methodOff, "initially triggered")
    }
    else
    {
        logdebug "${device.displayName} config say no, skip turning on"
    }
}

def turnOffIfInactive() 
{
    turnOffIfInactiveMain()
    turnOffIfInactiveSide()
}

def turnOffIfInactiveMain() 
{
    def configs = getConfigNames(getMode(), "mainlights", determineState())
    //turnOffGroupIfInactive(getMode(), mainlights, motionSensorsKeepOn, turnOffIfInactiveMain, determineState(), "mainlights") 
    turnOffIfInactiveGrp(configs, mainlights, motionSensorsKeepOn, turnOffIfInactiveMain) 
}

def turnOffIfInactiveSide() 
{
    def configs = getConfigNames(getMode(), "sidelights", determineState())
    //turnOffGroupIfInactive(getMode(), sidelights, motionSensorsKeepOn, turnOffIfInactiveSide, determineState(), "sidelights") 
    turnOffIfInactiveGrp(configs, mainlights, motionSensorsKeepOn, turnOffIfInactiveSide)
}

def turnOffIfInactiveGrp(configs, lights, sensors, methodOff)
{
    def doLights = getConfigDo(configs)
    def doTurnOn = getConfigDoTurnOn(configs)
    def stayOn = getConfigStayOn(configs)
    def configsidentifier = configs.lightname

    if(isAnySwitchOn(lights))
    {
        def activeSensors = motionsensors.findAll { it.currentMotion == "active" }
        if (doLights && !doTurnOn) 
        {
            loginfo ("Turn on is false, turning off lights.", "turnOffIfInactiveGrp")
            clearStateForced()
            turnOffLights(lights)
        } 
        else if (activeSensors.isEmpty()) 
        {
            loginfo ("${configsidentifier}, no active sensors, turning off lights.", "turnOffIfInactiveGrp")
            clearStateForced()
            turnOffLights(lights)
        } 
        else 
        {
            extendSchedule(getConfigstayOn, methodOff, "active sensors: ${activeSensors}, keeping lights on, self triggered")        
        } 
    }
    else
    {
        logdebug ("${configsidentifier}, no lights on, exiting", "turnOffIfInactiveGrp")
    }
}
/*
def turnOffGroupIfInactive(mode, lights, motionsensors, methodOff, determinedstate, configsidentifier) 
{
    if(isAnySwitchOn(lights))
    {
        def configs = getConfigNames(mode, configsidentifier, determinedstate)
        def doLights = getConfigDo(configs)
        def doTurnOn = getConfigDoTurnOn(configs)
        def stayOn = getConfigStayOn(configs)
        def configsidentifier = configs.lightname

        def activeSensors = motionsensors.findAll { it.currentMotion == "active" }
        //logdebug "${configsidentifier} doLights: ${doLights}"
        if (doLights && !doTurnOn) 
        {
            loginfo ("Turn on is false, turning off lights.", "turnOffIfInactiveGrp")
            clearStateForced()
            turnOffLights(lights)
        } 
        else if (activeSensors.isEmpty()) 
        {
            loginfo ("${configsidentifier}, no active sensors, turning off lights.", "turnOffIfInactiveGrp")
            clearStateForced()
            turnOffLights(lights)
        } 
        else 
        {
            extendSchedule(stayOn, methodOff, "active sensors: ${activeSensors}, keeping lights on, self triggered")        
        } 
    }
    else
    {
        logdebug ("${configsidentifier}, no lights on, exiting", "turnOffIfInactiveGrp")
    }
}
*/

def extendSchedule(duration, method, message = "")
{
    // Cancel any previously scheduled turn-off method
    unschedule(method)

    // Schedule turning off the light after the specified duration
    if(duration < 0 || duration == null)
    {
        duration = 10
        logdebug ("extending ${method} with ${duration} (defaulted), message: ${message}","extendSchedule")
        runIn(duration, method)
    }
    else
    {
        logdebug ("extending ${method} with ${duration}, message: ${message}","extendSchedule")
        runIn(duration, method)
    }
}
def isVirtual(device) {
    return device?.getTypeName()?.toLowerCase()?.contains("virtual") ?: false
}
def isContactOn(device) {
    return device.currentValue("contact") == "open"
}
def isAnyContactOn(devices)
{
    def anyOpen = devices.find { isContactOn(it) }
    return anyOpen != null
}
def isMotionActive(device) {
    return device.currentMotion == "active"
}
def isAnyMotionActive(devices)
{
    def anyActive = devices.find { it.currentMotion == "active" }
    return anyActive != null
}
def isSwitchOn(device) {
    return device.currentValue("switch") == "on"
}
def isAnySwitchOn(devices)
{
    def anyOn = devices.find { it.currentValue("switch") == "on" }
    return anyOn != null
}
def isColorDevice(device) {
    return device.hasCapability("ColorControl")
}
def isWorkday() {
    // Get the current date and day of the week
    def today = new Date()
    def dayOfWeek = today[Calendar.DAY_OF_WEEK]
    // Monday to Friday are considered workdays
    return (dayOfWeek >= Calendar.MONDAY && dayOfWeek <= Calendar.FRIDAY)
}
def isDinnerTime(){
    //return isTimeBetween("12:00", "16:00");
    return isTimeBetween("18:00", "19:00");
}
def isEarlyGoToWorkTime(){
    return isTimeBetween("04:20", "05:20") && isWorkday();
}

def isTimeBetween(timeStart, timeEnd) {
    def now = new Date()
    def start = parseTime(timeStart)
    def end = parseTime(timeEnd)

    // Adjust end time if it is before the start time (for times that cross midnight)
    if (end < start) {
        end += 24 * 60 * 60 * 1000 // add a day in milliseconds
    }

    return now >= start && now <= end
}

def parseTime(timeStr) {
    def timeParts = timeStr.split(':')
    def now = new Date()
    return new Date(now.year, now.month, now.date, timeParts[0].toInteger(), timeParts[1].toInteger())
}

def turnOffLights(lights, comment = "")
{
    setStayOnState(1)
    lights.each { light ->
        loginfo "OFF ${light} ${comment}"
        light.off()
    }
}

def jsonEvent(evt) {
    def eventInfo = [
        name: evt.name,             // Name of the event (e.g., "motion", "switch", etc.)
        value: evt.value,           // Value of the event (e.g., "active", "on", etc.)
        displayName: evt.displayName,// Display name of the device that generated the event
        deviceId: evt.deviceId,      // The ID of the device that generated the event
        descriptionText: evt.descriptionText,  // A human-readable description of the event
        source: evt.source,          // Source of the event (e.g., "DEVICE", "APP")
        isStateChange: evt.getIsStateChange(),    // Indicates if the event resulted in a state change
        physical: evt.physical,      // Whether the event was triggered by a physical action
        installedAppId: evt.installedAppId,    // The installed app ID related to the event (if any)
        hubId: evt.hubId,            // The hub ID where the event originated
        locationId: evt.locationId   // The location ID of the event
    ]

    def jsonOutput = JsonOutput.toJson(eventInfo)
    log.debug "Event Info in JSON: ${jsonOutput}"
    return jsonOutput
}

def getConfigBrightness(nms){
    def result = settings["${nms.brightness}"] ?: 100
    //logdebug "get cfg: [${nms.brightness} = ${result}]"
    return result;
}
def getConfigColor(nms){
    def result = settings["${nms.color}"]
    //logdebug "get cfg: [${nms.color} = ${result}]"
    return result;
}
def getConfigDo(nms){
    def result = settings["${nms.do}"]
    logdebug "get cfg: [${nms.do} = ${result}]"
    return result;
}
def getConfigDoTurnOn(nms){
    def result = settings["${nms.turnon}"]
    logdebug "get cfg: [${nms.turnon} = ${result}]"
    return result;
}
def getConfigStayOn(nms){
    def result = settings["${nms.stayon}"]
    //logdebug "get cfg: [${nms.stayon} = ${result}]"
    return result;
}

def getConfigNames(modename, lightname, statename)
{
    def result = []

    if(includedModes && includedModes.contains(modename))
    {
        if ([lightname, statename, modename].every { it != null }) 
        {
            def isValidLight = lightGroupArray().any { it[0] == lightname }
            def isValidState = stateGroupArray().any { it[0] == statename } 
            if(isValidLight && isValidState)
            {
                def l = lightGroupArray().find { it[0] == lightname }[1]
                def s = stateGroupArray().find { it[0] == statename }[1]
                //logdebug l
                //logdebug s
                def stateDescription = ""
                def base = "${modename}_${lightname}_${statename}"
                def titleGrpState = "[${modename.toUpperCase()}-💡${l.toLowerCase()}] ${s.toLowerCase()}"
                result = [
                    title: titleGrpState,
                    statedescription: stateDescription,
                    base: "${base}",
                    do: "${base}_do",
                    turnon: "${base}_turn_on",
                    brightness: "${base}_brightness",
                    color: "${base}_color",
                    stayon: "${base}_stayon",
                    lightname: lightname
                ]
            }
            else
            {
                logdebug "❌not found in arrays, modename: ${modename}, lightname : ${lightname} : ${isValidLight}, statename: ${statename} : ${isValidState}"
            }
        }
        else
        {
            result = []
        }
    }
    else
    {
        loginfo "${modename} not in ${includedModes}, do nothing"
        result = []
    }

    return result
}


def hexToHSL(hex) {
    // Remove the # symbol if present
    hex = hex.replace("#", "")

    // Convert hex to RGB
    int r = Integer.valueOf(hex.substring(0, 2), 16)
    int g = Integer.valueOf(hex.substring(2, 4), 16)
    int b = Integer.valueOf(hex.substring(4, 6), 16)

    float rPct = r / 255.0
    float gPct = g / 255.0
    float bPct = b / 255.0

    float max = Math.max(rPct, Math.max(gPct, bPct))
    float min = Math.min(rPct, Math.min(gPct, bPct))

    float h = 0
    float s = 0
    float l = (max + min) / 2

    if (max != min) {
        float d = max - min
        s = (l > 0.5) ? d / (2.0 - max - min) : d / (max + min)

        if (max == rPct) {
            h = (gPct - bPct) / d + (gPct < bPct ? 6 : 0)
        } else if (max == gPct) {
            h = (bPct - rPct) / d + 2
        } else if (max == bPct) {
            h = (rPct - gPct) / d + 4
        }

        h /= 6
    }

    h = Math.round(h * 100)
    s = Math.round(s * 100)
    l = Math.round(l * 100)

    return [hue: h, saturation: s, level: l]
}


def getStateForced(){
    return state.forced
}

def setStateForced(){
    if(!state.forced)
    {
        state.forced = true
        logdebug "state.forced turned ${getStateForced()}."
    }
}

def clearStateForced(){
    if(state.forced)
    {
        state.forced = false
        logdebug "state.forced turned ${getStateForced()}."
    }
}

def getStayOnState(){
    return state.stayOn
}

def setStayOnState(seconds){
    state.stayOn = seconds
}
