# Example: Expected Lowering of `user-card.vm`

Assume the typed model:

```java
public record UserPage(User user) {}
public record User(String displayName, boolean admin, List<String> roles) {}
```

The source:

```velocity
<h2>$user.displayName</h2>
#if($user.admin)
  <span class="badge">Administrator</span>
#else
  <span class="badge">User</span>
#end
```

should approximately normalize into IR like:

```text
WriteStatic C0                    ; "<h2>"
LoadParam user : User
GetProperty User.displayName : String [DIRECT_RECORD_ACCESSOR]
WriteValue escape=HTML_TEXT
WriteStatic C1                    ; "</h2>\n"
LoadParam user : User
GetProperty User.admin : boolean [DIRECT_RECORD_ACCESSOR]
BranchFalse L_else
WriteStatic C2                    ; administrator fragment
Jump L_end
L_else:
WriteStatic C3                    ; user fragment
L_end:
```

After static-chunk merging and typed lowering, generated code should be conceptually equivalent to:

```java
public void render(UserPage model, TemplateOutput out) throws IOException {
    User user = model.user();
    out.writeStatic(C0);
    out.writeHtml(user.displayName());
    out.writeStatic(C1);
    if (user.admin()) {
        out.writeStatic(C2);
    } else {
        out.writeStatic(C3);
    }
    // loop lowered similarly
}
```

The actual compiler is free to produce different JVM instructions. This example specifies desired *semantic shape*: no runtime property resolver is needed for statically known record accessors, static HTML is not reconstructed per render, and output is streamed.
