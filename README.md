# Synergos

Shows a Java codebase as a society of objects, in the spirit of David West's
*Object Thinking*: object cubes instead of class boxes, messages instead of
imports, and red flags for central control instead of dependency-rule
violations. Inspired by Uncle Bob's [uml-viewer](https://github.com/unclebob/uml-viewer).

Milestones 1 (the "Lens") and 2a ("Authoring") are built. See
[docs/SPEC.md](docs/SPEC.md) for the whole plan.

## Run

Needs Java 21 and Maven.

```bash
./syg ~/Repos/railcount                     # scan, serve on localhost:7070, open the browser
./syg ~/Repos/railcount --port 8080 --no-open
./syg ~/Repos/railcount --dump model.json   # scan only, write the model
./syg --list                                # which viewers are running, where, for how long
./syg --stop railcount                      # close one: by port, repo path or folder name, or all
```

`syg` builds the jar the first time, and again whenever the sources change.
The scanner asks Maven for the target repo's dependency classpath once and
caches it in `~/.cache/synergos/` until that repo's `pom.xml` changes.

The viewer writes into the scanned repo only when you save a cube's text, and
then only that one class's Javadoc comment. Git is the undo.

Each running viewer leaves a note about itself in `~/.cache/synergos/running/`
and removes it when it exits (Ctrl+C, `kill`, or `--stop`). If a viewer is
killed so hard it can't clean up after itself, the next `--list` notices that
its process is gone and drops the note.

## What you see

- **Society.** Every class is a cube and every interface a dashed role. Arrows
  are messages. Dashed brown arrows are static procedure calls. Hollow
  triangles mean "is a kind of". Objects that talk to each other cluster
  together. Drag a cube to pin it; the table remembers where you put things.
  Filters: tests, nested classes, kind-of lines, creations, library objects.
- **Cube.** Click a cube to unfold its six faces around the CRC card:
  description, contracts, knowledge, afferent messages and events. Every call
  site links to the source.
- **Through its eyes.** Double-click a cube, or use the button on the cube, to
  see the world as that object sees it: who sends it messages, whom it sends
  messages to, what it is a kind of, and what it creates. Click a neighbour to
  walk over to it.
- **Flags.** Five red flags, each tied to a principle: refused message, puppet
  master, procedure bag, manipulates others, controller name.
- **Writing cubes.** The description, stereotype and responsibilities live in
  each class's Javadoc. Existing Javadoc prose counts as the description as it
  is. Edit them on the cube and **Save to Javadoc**:

  ```java
  /**
   * A conveyor that carries transactions past a stamp, one by one, and reports how many it stamped.
   *
   * @responsibility Stamp every unstamped transaction in a file. {@link Stamp}
   *     {@link #drive(TransactionsFile)}
   * @responsibility Report how many were stamped. {@link PrintStream}
   * @stereotype coordinator
   */
  ```

  - Descriptions are short third-person definitions, in West's style: a kind
    of thing, then what sets it apart. See "Writing cube text" in the spec.
  - `{@link Type}` names a collaborator, and `{@link #message}` (optional) the
    message that carries the responsibility out. The editor has chips that
    insert both.
  - Responsibilities written on an interface show, faded, on every class that
    plays that role.
  - Cubes with no responsibilities of their own are drawn hollow. The header
    counts how many are written.
  - **Contradictions** (amber) are places where the text and the code disagree
    in a way the code can prove: a linked collaborator the object never
    reaches, a linked message it doesn't have, a message no responsibility
    links (only once you start linking messages), an unknown stereotype.
  - If the Javadoc changed on disk while you were editing, saving is refused.
    "Rescan now" keeps your draft.

Keys: `/` find a cube, `R` rescan, `Esc` close or go back, `⌘/Ctrl+Enter` save
an edit.

### One-time settings in the repo you write cubes in

The tool won't change these for you:

- **Checkstyle** reports unknown Javadoc tags. Allow them in `checkstyle.xml`:
  `<module name="JavadocType"><property name="allowUnknownTags" value="true"/></module>`.
  In railcount this matters for the `wall` profile.
- **IntelliJ** marks unknown tags as "Wrong tag". Add `responsibility` and
  `stereotype` under Settings → Editor → Inspections → Java → Javadoc →
  Declaration has Javadoc problems → Additional Javadoc Tags.

## Develop

```bash
mvn test                                        # unit tests, fixtures, self-check
SYG_RAILCOUNT=~/Repos/railcount mvn test        # plus acceptance on railcount
```

The self-check runs the viewer on its own code and expects no red flags.
The server only answers requests addressed to `127.0.0.1` or `localhost`, and
only accepts changes as JSON posts from its own page.
