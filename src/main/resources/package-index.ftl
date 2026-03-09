<!DOCTYPE html>
<html lang="en">
    <head>
        <meta charset="UTF-8" />
        <title>Index of ${currentPath}</title>
        <style>
            body {
                font-family: monospace;
                background: #ffffff;
                color: #000000;
                padding: 20px;
            }

            h1 {
                font-size: 18px;
                font-weight: normal;
            }

            a {
                text-decoration: none;
                color: #0000ee;
            }

            a:visited {
                color: #551a8b;
            }

            hr {
                border: none;
                border-top: 1px solid #000;
                margin: 10px 0;
            }

            ul {
                list-style: none;
                padding-left: 0;
            }

            li {
                line-height: 1.6;
            }

            #snapshot-toggle {
                display: flex;
                align-items: center;
            }

            #snapshot-checkbox {
                margin-right: 0.5rem;
            }
        </style>
    </head>
    <body>
        <h1>Index of ${currentPath}</h1>
        <hr>
        <#if showSnapshotToggle?? && showSnapshotToggle>
            <div id="snapshot-toggle">
                <input type="checkbox" id="snapshot-checkbox" />
                <label for="snapshot-checkbox">
                    Show snapshot versions
                </label>
            </div>
        </#if>
        <ul>
            <#if parentPath?? && parentPath?has_content>
                <li><a href="${parentPath}/index.html">../</a></li>
            </#if>

            <#list directories as dir>
                <li
                    <#if dir?ends_with("-SNAPSHOT")>
                        class="snapshot-item"
                    </#if>
                >
                    <a href="./${dir}/index.html">${dir}/</a>
                </li>
            </#list>
        </ul>
        <hr>
        <#if showSnapshotToggle?? && showSnapshotToggle>
            <script>
                const snapshotCheckbox = document.getElementById("snapshot-checkbox");
                const snapshotItems = document.querySelectorAll(".snapshot-item");

                const toggleSnapshotItemsState = () => {
                    snapshotItems.forEach(item => {
                        item.style.display = snapshotCheckbox.checked ? "list-item" : "none";
                    });
                }
                snapshotCheckbox.addEventListener("change", toggleSnapshotItemsState);
                toggleSnapshotItemsState();
            </script>
        </#if>
    </body>
</html>
