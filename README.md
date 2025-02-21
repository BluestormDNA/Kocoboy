![Platform android](http://img.shields.io/badge/platform-android-green.svg?style=flat)
![Platform ios](http://img.shields.io/badge/platform-ios-silver.svg?style=flat)
![Platform js](http://img.shields.io/badge/platform-js-yellow.svg?style=flat)
![Platform desktop/jvm](http://img.shields.io/badge/platform-desktop/jvm-orange.svg?style=flat)

# KocoBoy
An experimental Kotlin Multiplatform, Compose Multiplatform, GameBoy Emulator that targets Android, iOS, Desktop and JS/WASM.

<table>
  <tr>
    <td width="33%"> <img src="artwork/ui.png"></td>
    <td width="33%"><img src="artwork/sidebar.png"></td>
    <td width="33%"><img src="artwork/run.png"></td>
   </tr> 
</table>


> [!NOTE]  
> KocoBoy can run many games, demos and tests but it is not a M-Cycle or micro-ops accurate emulator.  
> Accuracy and syncronization between the various memory mapped devices relies on hardcoded fixed values and varies from 4 to 24 CPU cycles depending on the executed opcode or hardware interrupt.

## Compatibility

Game Boy catalog compatibility support is focused on the most popular cartridges types (MBCs 1,2,3 and 5).  
The full list goes as follows:

|Supported Cartridge Types  | Unsupported Cartridge Types |
|--|--|
|00h  ROM ONLY  |  08h  ROM+RAM |
|01h  MBC1  |  09h  ROM+RAM+BATTERY |
|02h  MBC1+RAM  |   0Bh  MMM01|
|03h  MBC1+RAM+BATTERY  |  0Ch  MMM01+RAM |
|05h  MBC2  |  0Dh  MMM01+RAM+BATTERY |
|06h  MBC2+BATTERY  | 1Ch  MBC5+RUMBLE |
|0Fh  MBC3+TIMER+BATTERY  | 1Dh  MBC5+RUMBLE+RAM |
|10h  MBC3+TIMER+RAM+BATTERY  | 1Eh  MBC5+RUMBLE+RAM+BATTERY |
|11h  MBC3  | 20h  MBC6 |
|12h  MBC3+RAM  | 22h  MBC7+SENSOR+RUMBLE+RAM+BATTERY |
|13h  MBC3+RAM+BATTERY  | FCh  POCKET CAMERA |
|19h  MBC5  | FDh  BANDAI TAMA5 |
|1Ah  MBC5+RAM  | FEh  HuC3 |
|1Bh  MBC5+RAM+BATTERY  |  FFh  HuC1+RAM+BATTERY |

> [!NOTE]  
> SRAM save files are not supported at the moment so your progress on games will be lost on exit.


## Using the emulator

Run the App, Click the cog / settings icon to see the sidebar.  
On the sidebar you can Load a rom, Power On/Off or select a theme.  
Click again the cog / settings icon to collapese the sidebar.  
 
On platforms with touch input you can actually use the UI as an input.  
On devices with keyboard input is mapped as:

* D-Pad UP: **W**
* D-Pad Left: **A**
* D-Pad Down: **S**
* D-Pad Right: **D**
* A: **K**
* B: **L**
* Start: **P**
* Select: **O**

## About the project
The project is splitted in 2 modules:  
**core:** It contains just commonMain Kotlin code. It represents the full emulator core with CPU, PPU, Bus and other components.  
**composeApp:** A Compose Multiplatform gradle module it is mainly a commonMain centric module with a couple of per platform functions, for image and audio handling.

## Known issues
This is nowhere a full list of issues just some of them from a high view perspective:
* iOS lacks sound output (yet)
* Main loop and audio sync have quite room for improvement.
* There are little differences on TextAutoSize, shadows and UI in different platforms (Compose Multiplatform 1.8.0-alpha2 issues).

## FAQ

- Can i use this emulator to play?  
Yes you can, but you shouldn't. There are a lot of other more capable emulators out there.  
This is just a personal project to play with Kotlin Multiplatform / Compose Multiplatform

- Why are you using Compose Multiplatform 1.8.0 alphas.  
This is an experimental project, also TextAutoSize is only available there.

- Why are you using Compose Multiplatform 1.8.0-alpha2 instead of alpha3?  
Even thought they fixed things like shadow inconsistencies per platform, the AndroidX dependencies are bumped to Compose 1.8.0-beta1 and that breaks TextAutoSize in animated custom layouts.

- Why are the previews of composables on androidMain instead of commonMain?  
Because @Preview annotations on Android Studio only works there.

- Why you did *insert random thing here* on this way? I can totally do it better!  
Make a pull request!



