# Synergos: spec

Draft, updated after review on 2026-09-25. **M1 is built** (see the README); sections 7 and 9 describe it as built. Section 11 (M2) was rewritten on 2026-09-30 after deciding how cube text is written and kept. **M2a is built**; see the notes at the end of M2a.

A local tool that shows a Java codebase as a society of objects in the spirit of David West's *Object Thinking*, the way Uncle Bob's uml-viewer shows a Clojure codebase as UML components. It shows cubes instead of class boxes and messages instead of imports. Its red flags mark central control and data-thinking, not dependency-rule violations. It pairs with Claude Code, so you can discuss a design in the viewer and have the agent change the code to match.

Sources: Bob's uml-viewer README, a sample of *Object Thinking* (preface, introduction, ch. 3), and the author's own design-process notes. Neither is in this repository. The notes are the source of truth for the cube faces. Page numbers below refer to the book.

## 1. Decisions so far

- Target codebases are Java with Maven. The first test subject is `~/Repos/railcount`.
- Scope is Offer 1 (Cube Lens) as the core, Offer 2 (Cube Table) as its proposal mode, and Offer 3 (Object Theater) as phase 2.
- It's one Maven project: a Java 21 scanner plus a web server from the JDK, serving a browser front end in plain JS and SVG. There's no Node on this machine, so there's no front-end build step.
- Parsing uses JavaParser 3.28 with its symbol solver, so every message send resolves to the type that receives it. It resolves library types through the target repo's Maven classpath (`mvn dependency:build-classpath`). I checked that this works on railcount.
- The tool's code follows railcount's style: final classes, constructor injection, interfaces for roles, no static utility classes, no null, and objects that print themselves instead of exposing getters. The tool is run on its own code as a check (section 10).
- The tool is named **Synergos** (Greek *synergos*, "fellow worker", i.e. collaborator). The command is `syg`, and the Java package is `com.vzurauskas.synergos`. It was called `ot-viewer` / `otv` until 2026-10-01; the new name was checked for clashes in package registries and on the web first.
- The Contracts face (3) stays empty until its meaning is clear.
- No "know something" flag, and no automated check for at most one collaborator per responsibility. More heuristics can be added later.
- Decided 2026-09-30: authored cube text lives in the class's Javadoc as custom tags (`@responsibility`, `@stereotype`), with collaborators as `{@link}`s. The viewer edits that Javadoc directly. Agent suggestions stay in a local cache until you accept them. Details in section 11.

## 2. Milestones

| | What you get |
|---|---|
| **M1: Lens** | `syg ~/Repos/railcount` opens a browser showing the society of objects, a cube for every class (six faces, filled in from the code), red flags, and source snippets. It's read-only. |
| **M2a: Authoring** | Description, stereotype and responsibilities read from and written to Javadoc, edited in the viewer. Contradictions between that text and the code. Progress: how many cubes are written. |
| **Housekeeping: running viewers** | **Built 2026-10-01.** `./syg --list` shows every running viewer with its repo, port and uptime. `./syg --stop <port \| repo \| all>` closes them. Each viewer records itself in `~/.cache/synergos/running/` when it starts and removes the record when it exits. A record whose process is gone is cleaned up the next time anyone lists, and so is one whose process id now belongs to a different process (start times are compared). `--stop` matches a port, a repo path or a repo folder name, or `all`. It asks the viewer to exit, and forces it after 5 seconds. Requested 2026-09-30, after several viewers were left running on different projects. |
| **M2b: Suggestions** | Your Claude Code session suggests descriptions and responsibilities for existing code, with evidence. You accept, edit or reject them in the viewer. |
| **M2c: Designed cubes and proposals** | Cubes with no code yet, what-if proposals over the real society, and "make it so", which hands a proposal to the agent. |
| **Phase 2: Theater** | Test runs are recorded as message traces and replayed as an animated play between object instances. Later, the agent role-plays stories that aren't coded yet. |

Sections 3–10 specify M1 in detail, and section 11 specifies M2a and M2b. M2c and Phase 2 are outlines, to be detailed when we get there.

## 3. The cube

The face order follows the author's design-process notes. In the viewer, a cube opens as an unfolded net:

```
      ┌─────┐
      │  2  │
┌─────┼─────┼─────┬─────┐
│  4  │  1  │  5  │  6  │
└─────┼─────┼─────┴─────┘
      │  3  │
      └─────┘
```

In the center is the CRC card. Above it is who I am, to the left is what I need to know, to the right is what I'm asked and what I announce, and below are my contracts (left empty for now).

| Face | Holds | M1: from the code | M2: written by you or the agent |
|---|---|---|---|
| 1 CRC | name, is-a-kind-of, responsibilities, collaborators | name, supertypes, and **collaborators** (project types this one sends messages to, with which messages) | `@responsibility` tags in the class's Javadoc, each linking its collaborators with `{@link}` |
| 2 Description | who I am, Wirfs-Brock stereotype | Javadoc; **stereotype guessed** by the rules below, showing the reason | the Javadoc description (existing prose counts); an `@stereotype` tag, replacing the guess |
| 3 Contracts | named contracts saying who should send which messages (West's examples: public, private) | left empty for now | to be decided |
| 4 Knowledge | what I need in order to do my job, and where it comes from | per public method, each item marked with its source, as in the design-process notes: **field** (given at construction, or made by me), **parameter**, **asks X** (a call to a collaborator whose result is used), or a **mix** (e.g. asks X using a parameter) | notes |
| 5 Afferent messages | messages I respond to, and who sends them | public methods, each with its senders (class, method, line) and whether they come directly or through one of my interfaces. `toString`/`equals`/`hashCode` are grouped under "identity" | none |
| 6 Events | what I announce, what I listen for, and registrations | **guessed**: methods I receive named `register*`, `subscribe*`, `add*Listener`, `on*`, and the matching calls I send (`registers with X`) | the real list |

**Guessing the stereotype** (the first rule that matches wins, and the reason is shown):
1. **Structurer:** it holds a collection or map of project objects.
2. **Coordinator:** it sends messages to 3 or more distinct project types.
3. **Interfacer:** it holds or uses a boundary type (`java.io`, `java.nio.file`, `java.net`, `java.sql`, jOOQ).
4. **Information holder:** it holds only value types (strings, numbers, dates, JSON) and sends no messages to project objects.
5. **Service provider:** anything else.

The Controller stereotype is never guessed. That's what the "Puppet master" flag is for.

### Example: `railcount`'s `Rail`, as the scanner would fill it

| Face | Content |
|---|---|
| 1 CRC | **Rail**. Kind of: none. Responsibilities: not written yet. Collaborators: `TransactionsFile` (transactions), `Transaction` (unstamped), `Stamp` (stamp). Foreign: `PrintStream`. |
| 2 Description | Coordinator, *guessed because it messages 3 project types*. No Javadoc. |
| 3 Contracts | Left empty for now. |
| 4 Knowledge | `stamp`: field, given at construction. `out`: field, given at construction, foreign. For `drive`: `transactions` is a parameter, the list of transactions comes from asking `TransactionsFile.transactions()`, and whether each is stamped comes from asking `Transaction.unstamped()`. |
| 5 Afferent | `drive(TransactionsFile)` ← `SwedStampService.perform`, `CashStampService.perform` |
| 6 Events | None detected. For comparison, `Filesystem` would show "accepts registrations: `register` ← `SwedTransactionsPackage`, `CashTransactionsPackage`". |
| Flags | None. |

## 4. Views (M1)

**Society (main view).** One node per cube:
- a solid square for a class
- a dashed square for an interface, which is a role
- a smaller square for a nested class, labelled `Outer.Inner`

Each node shows the name, a stereotype glyph, and red flag badges. Its border takes the color of the worst red flag. The fill is a faint tint per package, just enough to hint where a class lives without making the view about packages.

Edges:
- **Messages**, drawn from the sender to the type that receives the message (usually an interface). They are thicker when more distinct messages travel along them. Hovering lists each message with its call sites.
- **Is-a-kind-of** (implements or extends), drawn faintly.
- **Procedure calls** (static calls between classes), dashed. They are drawn differently on purpose because they are not messages.
- **Creates** (`new`), hidden by default.

The layout is force-directed, so objects that talk to each other cluster together. You can drag cubes around like CRC cards on a table, and the positions are remembered in the browser for each repo, so the table stays put between rescans.

Filters: tests, foreign types (shown as small ovals), nested classes, and creates edges. There is also a search box (`/`) and a Rescan button (`R`).

**Through its eyes (focus).** Double-click a cube to see the world as that object sees it. The object sits in the center, with the objects that message it on the left, the objects it messages on the right, the roles it plays above, and what it creates below. This view uses a fixed layout. `Esc` goes back.

**Cube.** Click a cube to open its six faces in a side panel. Each face says plainly when it's empty and why ("Responsibilities: not written yet"). Every message and call site links to the source.

**Source.** A panel shows the file with the line highlighted. "Open in IntelliJ" is a stretch goal.

**Flags.** A list of all red flags, grouped by flag. Click one to jump to its cube.

**Header.** Repo name and scan stats, e.g. `97 cubes · 214 messages · 93% of calls resolved`. The unresolved calls are listed on demand, so the tool never hides what it couldn't read.

## 5. Red flags (M1)

Each flag names the principle it comes from. Red means it clearly breaks one of West's principles. Amber means a heuristic that's worth a look. Thresholds are constants in M1 and become configurable in M2.

| Flag | | Principle | Rule | Expected on railcount |
|---|---|---|---|---|
| **Refused message** | red | Composability: *specialization by constraint* makes an object unusable without looking at how it works (ch. 3, p. 79) | a method that implements or overrides an inherited one and does nothing but throw `UnsupportedOperationException` | fires on `UnrecognizedService.command()` and `.description()` |
| **Puppet master** | red | Distributed cooperation replaces central control (ch. 3, pp. 81–83, the structure chart in Fig. 3-1) | sends messages to 5 or more distinct project types, and receives messages from at most one. Creating objects (`new`) doesn't count, so a composition root like `App` isn't flagged | none |
| **Procedure bag** | red | Everything is an object, so procedures are not a separate kind of thing (ch. 3, p. 67) | has non-private static methods (other than `main`) and no non-private instance methods | none |
| **Manipulates others** | red | The design-process notes: an object doesn't control, manage or manipulate any object other than itself | calls `setX(…)` on another object, or assigns to another object's field | none |
| **Controller name** | amber | Distributed control (ch. 3, p. 81) | class name ends in `Manager`, `Controller`, `Handler`, `Processor`, `Coordinator`, `Helper` or `Util(s)`. Names ending in -er generally are not flagged, because `Butler` and `Header` are fine | none |

railcount is already written in an object-thinking style, so only one flag is expected to fire on it. The fixtures in section 10 prove that each flag fires when it should.

Left out on purpose: a "know something" flag (West's own cube examples list responsibilities that return things) and a check for at most one collaborator per responsibility (a habit to keep in mind, not something to automate).

## 6. Scanner

- **Source roots.** It finds every `src/main/java` under the repo root, so multi-module Maven projects work. It also finds `src/test/java`, whose classes are marked as tests. In the society view, tests are hidden by default.
- **Classpath.** It runs `mvn -q dependency:build-classpath` in the target repo and caches the result, keyed on the modification time of `pom.xml`. If that fails, the scan carries on and the lower resolution percentage shows in the header.
- **Project types.** These are types declared in the source roots. Everything else is foreign (JDK, cactoos, jOOQ, sturdy-store, and so on).
- **Message.** A method call, or a method reference like `Service::command`, whose resolved receiver is a project type. It's attributed to the class that contains the call. Calls inside lambdas and anonymous classes count for the enclosing class. The edge goes to the receiver's *declared* type (e.g. `Rail` → `Stamp`, not `ConsultingStamp`), because that's what the sender knows about.
- **Not an edge:**
  - Calls to `this`, which count toward knowledge only.
  - Constructor calls, which are recorded as "creates" edges.
  - Static calls, which are recorded as procedure calls.
  - Calls to foreign types, which are recorded on the cube and drawn as edges only when the "foreign types" filter is on.
- **Nested classes** are their own cubes (everything is an object), grouped with their outer class. **Anonymous classes** fold into the enclosing class.
- **Unresolved calls** are kept with their file, line and expression, and listed in the UI.

## 7. The model (what the scanner produces)

A single JSON document, served at `/api/model`. `syg --dump model.json` writes it to a file. This is its shape as built, trimmed, for railcount's `Rail`:

```json
{
  "repo": { "name": "railcount", "path": "/Users/vzurauskas/Repos/railcount" },
  "stats": { "files": 82, "unparsed": [], "cubes": 77, "testCubes": 20,
             "messages": 405, "calls": 1426, "resolved": 0.9993 },
  "cubes": [{
    "id": "com.vzurauskas.railcount.Rail", "name": "Rail", "simpleName": "Rail",
    "package": "com.vzurauskas.railcount", "kind": "class", "outer": null, "test": false,
    "source": { "file": "src/main/java/com/vzurauskas/railcount/Rail.java", "line": 5 },
    "faces": {
      "crc": { "kindOf": [], "playedBy": [], "responsibilities": [],
               "collaborators": [{ "id": "com.vzurauskas.railcount.Stamp", "messages": ["stamp"] }],
               "procedures": [], "creates": [], "foreign": ["java.io.PrintStream"] },
      "description": { "javadoc": null,
                       "stereotype": { "value": "coordinator", "guessed": true, "why": "sends messages to 3 project types" } },
      "contracts": [],
      "knowledge": {
        "fields": [{ "name": "stamp", "type": "Stamp", "typeId": "com.vzurauskas.railcount.Stamp", "origin": "given" }],
        "methods": [{ "signature": "drive(TransactionsFile)", "line": 14, "items": [
          { "kind": "param", "name": "transactions", "type": "TransactionsFile" },
          { "kind": "field", "name": "stamp" },
          { "kind": "asks", "target": "com.vzurauskas.railcount.TransactionsFile", "name": "transactions",
            "receiver": "transactions", "receiverSource": "param", "usesParameters": false, "line": 17 }
        ] }]
      },
      "afferent": [{ "signature": "drive(TransactionsFile)", "line": 14, "static": false, "identity": false,
                     "senders": [{ "from": "com.vzurauskas.railcount.stamp.cash.CashStampService", "member": "perform",
                                   "target": "com.vzurauskas.railcount.Rail", "file": "…", "line": 27 }] }],
      "events": { "guessed": true, "accepts": [], "registersWith": [] }
    },
    "flags": []
  }],
  "edges": [{ "from": "…Rail", "to": "…Stamp", "kind": "message",
              "messages": [{ "name": "stamp", "sites": [{ "member": "drive", "file": "…", "line": 19 }] }] }],
  "foreign": ["com.vzurauskas.nereides.jackson.SmartJson", "…"],
  "unresolved": [{ "from": "…", "file": "…", "line": 46, "expression": "…" }]
}
```

A sender whose `target` isn't the cube itself sent the message through a role the cube plays (or a type that inherits it), and the viewer shows it as "via …". Edge kinds are `message`, `procedure`, `creates`, `write`, `kindOf` and `foreign`.

The model never writes anything into the scanned repo in M1.

## 8. Running it

```bash
./syg ~/Repos/railcount            # build if needed, scan, serve on localhost:7070, open the browser
./syg ~/Repos/railcount --port 8080 --no-open
./syg ~/Repos/railcount --dump model.json   # scan only
```

`syg` is a shell script. It runs `mvn -q package` if the jar is missing or out of date, then runs `java -jar target/synergos.jar`. Rescan (the button or `R`) scans the code again and reloads the model while keeping positions. Automatic reload on file changes comes in M2, when the agent is editing code.

## 9. Structure

```
pom.xml                      Java 21; javaparser-symbol-solver-core, jackson-databind, junit-jupiter
syg                          launcher script
src/main/java/com/vzurauskas/synergos/
  Main, Args, Action         the command line: Usage, Dump or Serve
  scan/                      JavaRepo (roots, classpath, parsing), JavaType/JavaMethod/JavaField,
                             Sites and Traffic (who reaches whom), Project (is-a-kind-of),
                             Cube with one Face object per side, Stereotype, Edges, Society
  flags/                     one class per flag, and Flags, which runs them all (never on tests)
  web/                       JDK HttpServer; routes /, /static/*, /api/model, /api/source, /api/rescan
src/main/resources/web/      index.html, synergos.js, society.js, focus.js, cube.js, drawer.js, util.js,
                             synergos.css, vendor/d3.min.js (ES modules, no build step)
src/test/…                   unit tests, the two vending machine fixtures, self-check, railcount acceptance
```

d3 (v7, ISC license) is saved into the repo once, for the force layout, zoom and pan. After that the tool runs offline.

Notes from building it:
- JavaParser can't resolve calls to record accessors (`site.name()`). When a method can't be resolved but the receiver's type can, the call still counts as a message to that type. That brought the tool's own code from 92% to 95% resolved; railcount has no records and resolves 99.9%.
- The self-check did its job: the puppet-master flag caught a `Stats` class that reached into five objects to collect numbers. Now each object describes itself.
- Three plain data carriers are Java records (`Site`, `Finding`, `SourceDir`), which expose accessors. Everything else follows railcount's style.
- The interfacer guess counts only channels to the outside world (streams, readers and writers, `Files`, sockets, JDBC, jOOQ's `DSLContext`), not values such as `URI` or `Path`.

## 10. Testing

- **Fixtures** (`src/test/resources/fixtures/`), built on the book's vending machine example:
  - `uvm-good`: `Dispenser`, `DispensingMechanism`, `DispenserCollection`, `Product`, and a `SystemClockCalendar` that products register with (ch. 3, pp. 79–87).
  - `uvm-bad`: `VendingController` (puppet master), `PriceUtils` (procedure bag), `ReadOnlyDispenser` (refused message), and a class that calls setters on another.

  Each flag gets a test showing that it fires on `uvm-bad` and stays quiet on `uvm-good`. The collectors for each face are tested on the same fixtures.
- **railcount acceptance.** This runs only when `SYG_RAILCOUNT` points at the repo. It checks that the flags match the expectations in section 5 and that at least 90% of calls are resolved.
- **Self-check.** `syg .` on this project reports no red flags.
- **UI.** A smoke test that `/api/model` returns valid JSON, plus a manual check in the browser against railcount.

**M1 is done when** `./syg ~/Repos/railcount`:
- opens the society view
- shows six filled-in faces for every cube you click
- shows exactly the red flags listed in section 5
- opens source snippets
- survives a rescan

## 11. M2: writing cubes

**Facts and opinions.** Faces 4 and 5, and the collaborators on face 1, are facts: they are generated from the code on every scan and never need approval. The description, the stereotype and the responsibilities are opinions, and someone has to author them. Each piece of opinion is in one of these states, and each state has one home:

| State | Produced by | Home | Shown as |
|---|---|---|---|
| Authored | you | the class's Javadoc (for a cube with no code yet: `.cubes/<Name>.md`, M2c) | plain |
| Inherited | you, on a role or a superclass | that type's Javadoc | faded, "from Stamp" |
| Suggested | the agent | local cache `.synergos/suggestions/` | dashed, with evidence and Accept / Edit / Reject |
| Guessed | the tool's heuristics (the stereotype only) | nowhere; worked out on every scan | dashed, "guessed" |
| Rejected | you, turning a suggestion down | the same cache | hidden; never suggested again |

Approval is a move, not a flag. Accepting a suggestion moves its text into the Javadoc, and from then on it is authored. A rescan just reads it, so there is nothing to approve again. A cube is **written** once it has at least one authored responsibility.

### M2a: authoring in Javadoc

**Format.** Existing Javadoc prose counts as the authored description, as it is.

```java
/**
 * I carry transactions past a stamp, one by one, and say how many got stamped.
 *
 * @responsibility Stamp every unstamped transaction in a file.
 *     {@link Stamp} {@link #drive(TransactionsFile)}
 * @responsibility Report how many were stamped.
 * @stereotype coordinator
 */
public final class Rail {
```

- `{@link Type}` names a collaborator. IntelliJ renames these links along with the class.
- `{@link #message}` is optional and names the message that carries the responsibility out. This is my addition; it makes the "serves no responsibility" check below possible.
- `@stereotype` takes one of the six Wirfs-Brock stereotypes and replaces the guess.
- Responsibilities written on an interface or a superclass show, faded, on every cube that plays that role or extends that class.

**Reading.** JavaParser already parses this (checked): each `@responsibility` is a block tag of its own, and each `{@link}` is an inline tag. A type link resolves through the file's imports, then its package, then a unique simple name in the project. A link that doesn't resolve stays as text and counts as a contradiction.

**Contradictions.** These are marked amber, and only what can be decided from the code counts:
- A responsibility links a type that the object never sends a message to, directly or through a kind of it.
- A responsibility links a message that the object doesn't respond to.
- If any responsibility of a cube links a message, then each public message that none of them links "serves no responsibility". Identity messages don't count.
- An `@stereotype` isn't one of the six.

They're listed on the cube, and in the flags drawer under a heading of their own, apart from red flags. There is no "this changed since you wrote it, is it still true?" nagging. A contradiction stays until the text or the code changes.

**Editing in the viewer.** You can edit the description, the stereotype and the responsibilities on the cube. Saving writes the class's Javadoc directly. This was the easier option; editing through proposals comes in M2c. Chips under the editor insert `{@link}`s to the cube's collaborators and messages.

**The Javadoc writer.** In M2a this is the only code that writes into the scanned repo. In M2b it's also how accepted suggestions get written.
- It touches only the Javadoc comment of one type, found by the id from the scan. The rest of the file stays byte for byte the same; the comment's exact position comes from JavaParser (checked).
- It owns the description and the `@responsibility` and `@stereotype` tags. Every other block tag (`@param <T>`, `@see`, `@since`, …) keeps its original lines and order. Cube tags go right after the description.
- Formatting:
  - Lines wrap at 100 columns, never inside a `{@link}`, and continuation lines are indented four spaces.
  - The comment is indented like its declaration, and the file keeps its line endings.
  - A `*/`, or an `@` at the start of a line, in your text gets escaped.
- Where there's no Javadoc yet, the comment is inserted above the declaration and its annotations. If you delete everything, the comment goes.
- The viewer sends the text you were editing. If the Javadoc on disk no longer matches it, for example because you edited it in the IDE in the meantime, the write is refused and the viewer asks you to rescan. Edits inside method bodies don't block a write.
- After writing, the repo is rescanned and a toast links to the changed lines. Git is the undo.

**Society.** Written cubes look solid and unwritten ones hollow. The header shows progress, e.g. "23 of 77 cubes written".

**Your build.** railcount needs two one-time settings, which the tool won't change for you:
- Checkstyle, which runs in the `wall` profile, reports unknown Javadoc tags. To allow them: `<module name="JavadocType"><property name="allowUnknownTags" value="true"/></module>`.
- IntelliJ marks unknown tags as "Wrong tag". Add `responsibility` and `stereotype` under Settings → Editor → Inspections → Java → Javadoc → Declaration has Javadoc problems → Additional Javadoc Tags.

**Server safety.** The server now writes files, so every request that changes something must come from the viewer's own page:
- a `POST` with `Content-Type: application/json`
- an `Origin` equal to the viewer's own address
- a `Host` of `127.0.0.1` or `localhost`

Without these checks, any web page open in your browser could post to it.

**Notes from building M2a** (2026-09-30):
- Contradictions reuse the flag machinery: they're findings with the id `contradiction`, kept apart from red flags in the flags drawer and on the Flags button.
- Where only the code can decide, the rules were narrowed:
  - "Links a type it never reaches" counts messages, static calls, creations and field writes, with kinds of the target counting as the target. It isn't checked on interfaces, which have no code to check against.
  - "Serves no responsibility" only applies once a cube's *own* responsibilities link a message. Inherited links count towards covering its messages.
- Links resolve like Javadoc does, including JDK and library types imported by the file, so `{@link PrintStream}` is a valid collaborator.
- When a save is refused because the Javadoc changed on disk, "Rescan now" keeps the draft. The editor reopens on top of the fresh file and says what the file contains now, so you know what saving would replace.
- Unwritten cubes are drawn as wireframes. Written ones are filled, and a little richer in light mode than in M1 so they stand out.
- The guard applies to every route, not just to those that change something: all requests must be addressed to `127.0.0.1` or `localhost`.

### M2b: suggestions from the agent

There is no `claude` CLI on this machine, so the viewer doesn't call an agent itself. It leaves requests for your Claude Code session, which answers through the `syg` command. No skill needs installing: each request says what to run, and what it runs prints its own instructions.

1. **Ask.** "Suggest" works on one cube, or on everyone in the current through-its-eyes view, so converting a codebase can be a walk. It appends a request to `.synergos/requests.jsonl` and prints it on the viewer's output, e.g. `syg: request 7: run /…/syg /…/railcount --packet com.vzurauskas.railcount.Rail and follow its instructions`.
   - If Claude Code started the viewer in the background, it can watch that output and wake up.
   - Otherwise the request waits until you tell Claude Code to handle the viewer's requests.
2. **Packet.** `syg <repo> --packet <id>` prints what the agent needs, from the outside in. Method bodies come last.
   - the messages the object responds to, who sends each one, and the code around each call
   - the tests that reach it (the stories)
   - the roles it plays and its collaborators
   - its existing Javadoc and responsibilities already authored
   - suggestions you rejected before
   - the heuristics from the design-process notes (the tool will ship its own copy)
   - the answer format
3. **Answer.** The agent pipes JSON to `syg <repo> --suggest <id>`: a description, a stereotype and responsibilities, each with its evidence (messages, senders, tests). The command checks:
   - the JSON itself
   - that every cited message, sender and test exists
   - that every `{@link}` resolves

   It prints any errors so the agent can fix them. A valid answer is stored in `.synergos/suggestions/<id>.json` together with the cube's fingerprint.
4. **Review.** The viewer polls for changes and shows suggestions dashed, next to their evidence.
   - **Accept** writes the text into the Javadoc (with the writer above) and removes it from the cache.
   - **Edit** opens the editor with the text filled in.
   - **Reject** remembers the text so it isn't suggested again.
   - **Accept all** does the whole cube at once.
5. **Stability.** The fingerprint covers only the cube's outside: its messages, roles, senders and collaborators. It leaves out method bodies, fields and Javadoc, so accepting one suggestion doesn't disturb the rest. While the fingerprint holds, suggestions stay word for word the same across rescans. When it changes, they're marked "outdated" and stay until you ask again.
6. `.synergos/` gets its own `.gitignore` containing `*`, so it stays out of git without touching the repo's own `.gitignore`.

### M2c outline: designed cubes and proposals

- Cubes with no code yet live in `.cubes/<Name>.md` (committed) and are drawn dashed in the society. Their text has the same states.
- Proposals, in `.cubes/proposals/<name>.md`, are what-ifs drawn over the real society: new cubes dashed, removed ones greyed, changed ones marked.
- "Make it so" hands a proposal to the agent.
  - The tool copies authored text into the new classes' Javadoc word for word, and retires the `.cubes/` files of cubes that now have code.
  - It checks that no authored responsibility was lost: each one ends up in some cube, or you dropped it explicitly.
- The agent gets a rule for everyday coding: never write `@responsibility` or `@stereotype` tags on its own; suggest them instead.
- `.synergos/context.json`, right-click actions, and live reload when sources change, as in the earlier outline.

### Testing M2

- The writer, against expected output files: inserting where there was no Javadoc; replacing the description while keeping `@param <T>` and `@see`; a nested class; CRLF line endings; escaping; refusing when the Javadoc changed.
- Reading: tags, link resolution, inheritance from roles, and each contradiction.
- Suggestions:
  - bad citations are refused
  - a change inside a method body keeps suggestions; a changed signature marks them outdated
  - rejected texts stay rejected
- The server refuses writes with a foreign `Origin`, `Host` or content type.
- The self-check still passes.

## 12. Phase 2 outline: Object Theater

- **Recorder.** A Java agent attached to surefire test runs (from the command line, with no pom changes). It records the messages sent between project object instances for each test method: sender instance, receiver instance, message, depth. The output goes to `.synergos/traces/<Test>#<method>.jsonl`.
- **Stage view.** Pick a test, which plays the role of a story. Its objects appear as actors (`:SwedTransaction#2`), and messages animate step by step with play, pause and step controls. Each actor's cube is shown alongside.
- **Role-play.** For a proposal's story that isn't coded yet, the agent plays each cube using only what's on its faces. When an object needs knowledge it doesn't have, or reaches into another object, the play stops and flags it.

## 13. Later ideas (not planned)

- West's Human object (p. 71): interfacers that read from and write to the user connect to a synthesized `Human` cube (provides information, makes a selection, confirms).
- A semantic net view for domain vocabulary.
- A 3-D cube you can rotate with the arrow keys.
- Open in IntelliJ.
- Gradle support.
- Drilling down by package for large codebases.
- A coverage overlay, like Bob's CRAP colors.
- Asking for suggestions straight from the viewer with `claude -p`, if the Claude Code CLI gets installed.
- Linking responsibilities to the tests (stories) that exercise them.
- Marking a cube "not worth writing", so it leaves the conversion queue.
- The Contracts face, once its meaning is clear.
- More red flags, as the design process settles.

