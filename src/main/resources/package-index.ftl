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
        </style>
    </head>
    <body>
        <h1>Index of ${currentPath}</h1>
        <hr>
        <ul>
            <#if parentPath?? && parentPath?has_content>
                <li><a href="${parentPath}/index.html">../</a></li>
            </#if>

            <#list directories as dir>
                <li><a href="./${dir}/index.html">${dir}/</a></li>
            </#list>
        </ul>
        <hr>
    </body>
</html>