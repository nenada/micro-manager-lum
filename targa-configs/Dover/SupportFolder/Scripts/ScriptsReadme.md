# Scripts

There is a top-level `Initialize.lua` script which is responsible for initializing and homing all axes. 

Each axis has its own script `[AxisName].lua` containing functions for initializing and homing the axis. These functions are called from the top-level `Initialize.lua` script.

The scripts are auto-generated using a set of template scripts (found in the `Templates/Scripts` folder) during first time startup or during re-configuration. During re-configuration, existing scripts will be updated.

As the scripts are auto-generated, there are some conventions which must be followed in terms of script layout.

The `Initialize.lua` script has the following sections, identified by code comments (which should not be removed):

Near the top of the file, there is a 'require' section which loads each individual axis script. e.g.

```Lua
-- Begin Require Axis Scripts

require("XAxis")
require("YAxis")

-- End Require Axis Scripts
```

Within the `InitializeAllAxes()` function, there is an 'initialize' section which initializes each individual axis. e.g.

```Lua
-- Begin Initialize Axes

    InitializeXAxis()
    InitializeYAxis()

-- End Initialize Axes
```

Note that each individual axis initialize function is expected to be named `Initialize[AxisName]()`.

Within the `HomeAllAxes()` function, there is an 'initialize' section which initializes each individual axis. e.g.

```Lua
-- Begin Home Axes

    HomeXAxis()
    HomeYAxis()

-- End Home Axes
```

Note that each individual axis home function is expected to be named `Home[AxisName]()`.

If you maintain these conventions, you will be able to use the _Reconfigure_ command from the MotionSynergyGUI to add/remove/edit products.
If you are happy to manually update the configuration and associated scripts, then you can ignore these conventions and use a scripting layout
that suits your application.

There are also a number of common scripts used by all products:  

  * `Utilities`: Various utilities to be used by scripts and other Lua modules.
  * `ScriptPrelude.lua`: A module with code that should be called at the start of any top-level script.

## Writing scripts

When making a new top-level script, make sure to put `require("ScriptPrelude").Run()` at the top to install the abort handler and load emulation scripts. See `ScriptPrelude.lua` for more details.
