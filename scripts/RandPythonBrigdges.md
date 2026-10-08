# Start Python and R bridges for one project

If you need a Python or R bridge, put a Groovy lifecycle script beside your project:

```text
MyProject.nodus
MyProject.groovy
```

Use the same basename, including capitalization. Nodus runs the script with
`openProject=true` while opening the project and with `closeProject=true` during
a normal close. It supplies `nodusMapPanel` as the Java API entry point. If you
already have a project hook, add the bridge blocks to it rather than replacing
its other actions. The opening hook runs before `project.isOpen()` becomes true.

## Choose which bridges to enable

Nodus generates a Py4J token and a J4R key at startup and saves them in
`~/.nodus9.properties` (the current user's home directory on Windows, macOS,
or Linux). Existing valid values are reused. The properties are named
`bridge.py4j.token` and `bridge.j4r.key`.

Copy the example below into `MyProject.groovy`. Set `enablePython` and
`enableR` at the top to choose which bridges start when that project opens.
Both bridges are optional; copying this script does not enable the R bridge
unless you change `enableR` to `true`. If another Nodus instance already uses
the example ports, choose different ports in the hook and matching client.

The preferences file contains credentials. Nodus restricts it to owner-only
access where POSIX file permissions are available; on Windows, keep it in
your private user profile. To rotate the credentials, close Nodus, remove
those two property lines from the file, and start Nodus again.

The **Py4J** listener below binds to `127.0.0.1`. The bundled **J4R 1.1.1**
library binds its public and internal sockets on all network interfaces; its
integer key does not make that a local-only service. Enable J4R only on a
trusted or firewalled host. Do not expose its ports to an untrusted network.

## Example `MyProject.groovy`

```groovy
import java.net.InetAddress
import j4r.net.server.JavaGatewayServer
import j4r.net.server.ServerConfiguration
import py4j.GatewayServer

boolean enablePython = true
boolean enableR = false

if (openProject) {
    if (enablePython) {
        String pythonToken = nodusMapPanel.getNodusProperties()
            .getProperty('bridge.py4j.token')
        if (!pythonToken) {
            throw new IllegalStateException('Missing bridge.py4j.token in .nodus9.properties')
        }
        GatewayServer pythonBridge = new GatewayServer.GatewayServerBuilder()
            .entryPoint(nodusMapPanel)
            .javaAddress(InetAddress.getByName('127.0.0.1'))
            .javaPort(25333)
            .authToken(pythonToken)
            .build()
        try {
            pythonBridge.start()
            nodusMapPanel.storeObject('project.pythonBridge', pythonBridge)
        } catch (Exception failure) {
            try {
                pythonBridge.shutdown()
            } catch (Exception cleanup) {
                failure.addSuppressed(cleanup)
            }
            throw failure
        }
    }

    if (enableR) {
        String rKeyText = nodusMapPanel.getNodusProperties()
            .getProperty('bridge.j4r.key')
        if (!rKeyText) {
            throw new IllegalStateException('Missing bridge.j4r.key in .nodus9.properties')
        }
        int rKey = Integer.parseInt(rKeyText)
        if (rKey <= 0) {
            throw new IllegalArgumentException('bridge.j4r.key must be positive')
        }
        ServerConfiguration config = new ServerConfiguration(
            1, 10, [18000, 18001] as int[], [50000, 50001] as int[], rKey)
        JavaGatewayServer rBridge = new JavaGatewayServer(config, nodusMapPanel)
        try {
            rBridge.startApplication()
            nodusMapPanel.storeObject('project.rBridge', rBridge)
        } catch (Exception failure) {
            try {
                rBridge.requestShutdown()
            } catch (Exception cleanup) {
                failure.addSuppressed(cleanup)
            }
            throw failure
        }
    }
}

if (closeProject) {
    JavaGatewayServer rBridge =
        (JavaGatewayServer) nodusMapPanel.retrieveObject('project.rBridge')
    if (rBridge != null) {
        try {
            rBridge.requestShutdown()
        } catch (Exception failure) {
            System.err.println("Could not stop the R bridge: ${failure.message}")
        } finally {
            nodusMapPanel.removeStoredObject('project.rBridge')
        }
    }

    GatewayServer pythonBridge =
        (GatewayServer) nodusMapPanel.retrieveObject('project.pythonBridge')
    if (pythonBridge != null) {
        try {
            pythonBridge.shutdown()
        } catch (Exception failure) {
            System.err.println("Could not stop the Python bridge: ${failure.message}")
        } finally {
            nodusMapPanel.removeStoredObject('project.pythonBridge')
        }
    }
}
```

The stored objects let the closing invocation stop the same server instances
that were started during opening. If either startup fails, Nodus reports the
script error; check for a port conflict or invalid key. The separate shutdown
handlers let a failure in one bridge leave the other cleanup running.

**Current lifecycle limit:** if the project database connection is lost, Nodus
skips the project close hook. Project closing alone may therefore leave an
enabled bridge running. Exit the Nodus process to close it in this case. A
future project-owned cleanup path should stop bridges independently of Groovy
hooks and database health.

## Connect clients

The bundled [Python example](example.py) reads `bridge.py4j.token` and connects
to `127.0.0.1:25333`. Install the matching client with
`python3 -m pip install py4j==0.10.9.2`, then run
`python3 scripts/example.py` from the repository root.

The bundled [R example](example.R) reads `bridge.j4r.key`. Install the included
[J4R client archive](J4R_1.1.1-228.tar.gz) in R, then run
`source("scripts/example.R")` from the repository root. It uses public ports
`18000:18001` and internal ports `50000:50001`. The R client disconnects when
it finishes; the project hook controls the server lifetime.
