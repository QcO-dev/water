![Water Programming Language Logo](https://repository-images.githubusercontent.com/385689309/445d2a70-a200-496e-9059-59274e38a5fa)


# Water Programming Language

## What is it?

Water is a compiled programming language, targetting the JVM. It is intended to allow for less boilerplate code and easier development.


## Development of Water
Water is currently being developed by a single member team (me, myself, and I) so updates and bug fixes may take a while.

## Dependencies
 - JCommander
 - ASM 9.0

## Examples

### Simple Hello World
```typescript
function main() {
    println("Hello, World!");
}
```

### Object Orientated Greeter
```typescript
class Greeter {
    var name = "";

    constructor(name: String) {
        this.name = name;
    }

    function greet() = "Hello, " + name;
}

function main(args: String[]) {
    println(new Greeter(args[0]).greet());
}

```
