import com.sun.source.util.JavacTask;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import javax.tools.*;
/** Parse only: deliberately does NOT claim Android symbol resolution/type checking. */
public final class ParseJava {
    public static void main(String[] args) throws Exception {
        JavaCompiler compiler=ToolProvider.getSystemJavaCompiler();
        if(compiler==null)throw new IllegalStateException("Use a JDK, not a JRE");
        List<Path> paths;
        try(var files=Files.walk(Path.of(args[0]))) { paths=files.filter(p->p.toString().endsWith(".java")).sorted().toList(); }
        DiagnosticCollector<JavaFileObject> diagnostics=new DiagnosticCollector<>();
        try(StandardJavaFileManager manager=compiler.getStandardFileManager(diagnostics,Locale.ROOT,StandardCharsets.UTF_8)) {
            JavacTask task=(JavacTask)compiler.getTask(null,manager,diagnostics,List.of("-proc:none","--release","17"),null,manager.getJavaFileObjectsFromPaths(paths));
            for(var ignored:task.parse()) { }
            long errors=diagnostics.getDiagnostics().stream().filter(d->d.getKind()==Diagnostic.Kind.ERROR).count();
            for(var diagnostic:diagnostics.getDiagnostics()) System.out.println(diagnostic);
            if(errors!=0)throw new IllegalStateException(errors+" Java syntax errors");
            System.out.println("PASS: parsed "+paths.size()+" Java source files. Syntax only; Android/Xposed types NOT checked.");
        }
    }
}
