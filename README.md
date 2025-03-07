# CheshkaServer
A server software implementation for the [Cheshka](https://github.com/minecraft8997/Cheshka) game.

## Running
Execute `java -jar CheshkaServer.jar` in your terminal or startup script.

**JDK 21 is required.** Place `-Dcheshka.server.showProperties=true` option before the `-jar` flag to view available configuration options. To be documented soon.

### Enabling Captcha challenge
To verify newcomers, you will need to obtain the [SimpleCaptcha](https://sourceforge.net/projects/simplecaptcha) library, the project license can be found [here](https://sourceforge.net/p/simplecaptcha/code/ci/master/tree/license.txt). Run CheshkaServer and follow the instructions from console.

Note that this dependency is not mandatory for server software operation.

## Building
Step 1. Ensure you have JDK 21 (or above) installed;

Step 2. Clone the repository: `git clone https://github.com/minecraft8997/CheshkaServer`;

Step 3. Import the project into IntelliJ IDEA;

Step 4. Obtain [SimpleCaptcha 1.2.1](https://sourceforge.net/projects/simplecaptcha/files/simplecaptcha-1.2.1.jar) (or update [`MANIFEST.MF`](https://github.com/minecraft8997/CheshkaServer/blob/master/src/META-INF/MANIFEST.MF) if you are using any other version of the dependency);

Step 5. Navigate to File -> Project Structure -> Libraries -> Click '+' sign -> Java (in New Project Library dialog) -> Select SimpleCaptcha's jarfile;

Step 6. Navigate to File -> Project Structure -> Artifacts -> Add -> JAR -> From modules with dependencies...

Step 7. Select the main class `ru.deewend.cheshka.server`;

Step 8. Under "JAR files from libraries" section, click "copy to the output directory and link via manifest", click OK;

Step 9. Under Output Layout, press Apply;

Step 10. Navigate to Build -> Build Artifacts... -> CheshkaServer:jar -> Build. CheshkaServer's jarfile will be located in the `out/artifacts/CheshkaServer_jar` folder.
