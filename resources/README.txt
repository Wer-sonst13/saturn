Hier legt der Build die Saturn-Mod ab - eine Jar je Minecraft-Version.

Der Dateiname nennt die Minecraft-Version, fuer die die Jar gebaut wurde:
  saturn-1.21.4.jar   ->  gehoert in jedes Fabric-1.21.4-Profil
  saturn-1.21.9.jar   ->  gehoert in jedes Fabric-1.21.9-Profil

Warum pro Version eine eigene? Yarn benennt zwischen den Minecraft-Versionen
um - Methoden und Klassen heissen dann anders. Eine Jar, die fuer 1.21.1
gebaut ist, findet auf 1.21.5 nichts, dann fehlen genau die Menues und Knopfe.

Gebaut werden sie mit buildmod.bat (alle Versionen) oder
buildmod.bat 1.21.4 (nur eine). Welche Versionen es gibt und mit welcher
Yarn-/Fabric-API-Fassung, steht in mod\versions.json.

Der Launcher legt die passende Jar automatisch in ein Fabric-Profil und
laesst sie sich nicht abschalten - sie gehoert zum Programm.