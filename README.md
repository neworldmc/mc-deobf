# mc-deobf
Deobfuscator for Minecraft

## Quick start

	mvn compile assembly:single
	java -jar target/mc-deobf-*.jar -m server.txt -i ~/mc/server-unpacked -o ~/mc/server-deobf -c

### Command line arguments

Mandatory options:

* `-m` specifies path to obfuscation mapping file
* `-i` specifies input directory. This is the directory where the game jar is
  unpacked, i.e. the directory that contains `META-INF`. Passing a jar file to
  this option won't work.
* `-o` specifies output directory

Optional options:

* `-c` indicates that non-class files should be copied to output directory. If
  not set, the output directory will be missing files required for the game to
  run.

## Features

* Works on vanilla clients and servers
* Works on vanilla clients with OptiFine. Unpack OptiFine jar in the directory
  where the vanilla client jar is unpacked and run the deobfuscator. Manual
  modification of some class files is needed to fix errors that occur during
  class loading. [dirtyJOE](http://dirty-joe.com/) is a good tool for this, but
  it only runs on Windows or Wine.
* Reasonable performance

## Where can I find the obfuscation mappings?

Look into `.minecraft/versions/$version/$version.json` for `client.txt` and
`server.txt` and you will see the URL. However, in some cases, the URLs can be
missing. For your convenience, URLs of obfuscation mappings are provided here.

* 1.14.4
  * Client: https://launcher.mojang.com/v1/objects/c0c8ef5131b7beef2317e6ad80ebcd68c4fb60fa/client.txt
  * Server: https://launcher.mojang.com/v1/objects/448ccb7b455f156bb5cb9cdadd7f96cd68134dbd/server.txt
