package com.vzurauskas.synergos;

import com.vzurauskas.synergos.flags.Contradictions;
import com.vzurauskas.synergos.flags.ControllerName;
import com.vzurauskas.synergos.flags.Flags;
import com.vzurauskas.synergos.flags.ManipulatesOthers;
import com.vzurauskas.synergos.flags.ProcedureBag;
import com.vzurauskas.synergos.flags.PuppetMaster;
import com.vzurauskas.synergos.flags.RefusedMessage;
import com.vzurauskas.synergos.running.Registration;
import com.vzurauskas.synergos.running.Registry;
import com.vzurauskas.synergos.scan.JavaRepo;
import com.vzurauskas.synergos.scan.MavenClasspath;
import com.vzurauskas.synergos.scan.Society;
import com.vzurauskas.synergos.web.Browser;
import com.vzurauskas.synergos.web.LiveModel;
import com.vzurauskas.synergos.web.Viewer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;

/**
 * The command line. It knows which action was asked for and composes the
 * objects that perform it.
 */
public final class Args {
    private static final int DEFAULT_PORT = 7070;

    private final List<String> args;

    public Args(String... args) {
        this.args = List.of(args);
    }

    public Action action() {
        if (args.isEmpty() || args.contains("--help") || args.contains("-h")) {
            return new Usage(System.out, "");
        }
        Path cache = Paths.get(System.getProperty("user.home"), ".cache", "synergos");
        Path running = cache.resolve("running");
        if (args.contains("--list")) {
            return new ListViewers(new Registry(running), System.out);
        }
        if (args.contains("--stop")) {
            return value("--stop")
                .<Action>map(target -> new StopViewers(new Registry(running), target, System.out))
                .orElseGet(() -> new Usage(System.err, "Say which viewer to stop: its port, its repo, or all."));
        }
        Path root = Paths.get(args.get(0)).toAbsolutePath().normalize();
        if (!Files.isDirectory(root)) {
            return new Usage(System.err, "Not a directory: " + root);
        }
        JavaRepo repo = new JavaRepo(root, new MavenClasspath(root, cache, System.err));
        Flags flags = new Flags(
            new RefusedMessage(),
            new PuppetMaster(),
            new ProcedureBag(),
            new ManipulatesOthers(),
            new ControllerName(),
            new Contradictions()
        );
        Optional<String> dump = value("--dump");
        if (dump.isPresent()) {
            return new Dump(new Society(repo, flags), Paths.get(dump.get()), System.err);
        }
        LiveModel model = new LiveModel(repo, flags);
        return new Serve(
            model,
            new Viewer(model, repo, value("--port").map(Integer::parseInt).orElse(DEFAULT_PORT)),
            new Browser(!args.contains("--no-open")),
            new Registration(running, root.toString()),
            System.out
        );
    }

    private Optional<String> value(String flag) {
        int index = args.indexOf(flag);
        if (index < 0 || index + 1 >= args.size()) {
            return Optional.empty();
        }
        return Optional.of(args.get(index + 1));
    }
}
