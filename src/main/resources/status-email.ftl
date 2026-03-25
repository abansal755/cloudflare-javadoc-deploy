<#-- Status email template for SES (HTML). -->
<!DOCTYPE html>
<html lang="en">
  <head>
    <meta charset="UTF-8" />
    <title>Javadoc Deployment Status</title>
    <style>
      body {
        margin: 0;
        padding: 0;
        background: #f3efe7;
        color: #1d232a;
        font-family: "Avenir Next", "Segoe UI", Arial, sans-serif;
      }
      .wrap {
        max-width: 860px;
        margin: 0 auto;
        padding: 28px 18px 40px;
      }
      .card {
        background: #fffdfa;
        border: 1px solid #ded6c8;
        border-radius: 20px;
        overflow: hidden;
        box-shadow: 0 18px 50px rgba(68, 49, 22, 0.12);
      }
      .header {
        padding: 28px 32px 22px;
        background: ${success?string("linear-gradient(135deg, #123f33 0%, #1e5a49 100%)", "linear-gradient(135deg, #4d1f24 0%, #7a2d35 100%)")};
        color: #fffdf8;
      }
      .header h1 {
        margin: 0;
        font-size: 34px;
        line-height: 1.1;
        letter-spacing: -0.02em;
        font-weight: 700;
      }
      .header p {
        margin: 10px 0 0;
        font-size: 14px;
        line-height: 1.5;
        color: rgba(255, 253, 248, 0.84);
        font-weight: 400;
      }
      .content {
        padding: 26px 32px 32px;
      }
      .hero-row {
        margin-bottom: 24px;
      }
      .status-badge {
        display: inline-block;
        padding: 8px 14px;
        border-radius: 999px;
        font-size: 12px;
        font-weight: 600;
        text-transform: uppercase;
        letter-spacing: 0.08em;
        background: ${success?string("#dff1e7", "#f9dfe1")};
        color: ${success?string("#174b3a", "#7a2d35")};
      }
      .section-title {
        margin: 28px 0 12px;
        font-size: 12px;
        font-weight: 600;
        text-transform: uppercase;
        letter-spacing: 0.1em;
        color: #7b6f60;
      }
      .summary-grid {
        width: 100%;
        border-collapse: separate;
        border-spacing: 0;
        border: 1px solid #e4dbc9;
        border-radius: 14px;
        overflow: hidden;
        background: #f8f4ec;
      }
      .summary-grid td {
        padding: 14px 18px;
        border-top: 1px solid #e4dbc9;
      }
      .summary-grid tr:first-child td {
        border-top: none;
      }
      .label {
        width: 180px;
        font-size: 11px;
        font-weight: 600;
        text-transform: uppercase;
        letter-spacing: 0.08em;
        color: #7f7467;
      }
      .value {
        font-size: 15px;
        color: #1d232a;
      }
      .code {
        font-family: "SFMono-Regular", "Menlo", "Consolas", monospace;
        font-size: 13px;
        background: #f2ede3;
        border: 1px solid #e0d6c6;
        border-radius: 8px;
        padding: 2px 8px;
        font-weight: 400;
      }
      .list {
        margin: 0;
        padding: 0;
        list-style: none;
      }
      .chip-block {
        padding: 8px;
        border: 1px solid #e7decf;
        border-radius: 14px;
        background: #fcfaf6;
      }
      .list li {
        margin: 0 0 10px;
        padding: 12px 14px;
        border: 1px solid #e7decf;
        border-radius: 12px;
        background: #fcfaf6;
      }
      .list li:last-child {
        margin-bottom: 0;
      }
      .package-card {
        margin-top: 12px;
        border: 1px solid #e7decf;
        border-radius: 14px;
        background: #fcfaf6;
        overflow: hidden;
      }
      .package-card-header {
        padding: 14px 16px;
        background: #f5efe5;
        border-bottom: 1px solid #e7decf;
      }
      .package-card-body {
        padding: 8px;
      }
      .package-name {
        margin: 0;
        font-size: 16px;
        font-weight: 500;
        color: #1b242c;
      }
      .artifacts {
        margin: 0;
        padding: 0;
        list-style: none;
      }
      .artifacts li {
        margin: 10px 0 0;
        padding: 0;
        border: none;
        background: transparent;
      }
      .artifact-chip {
        display: inline-block;
        margin: 8px;
        padding: 7px 10px;
        border-radius: 999px;
        background: #ece4d6;
        color: #2d3339;
        font-family: "SFMono-Regular", "Menlo", "Consolas", monospace;
        font-size: 12px;
        border: 1px solid #ddd2c1;
        font-weight: 400;
      }
      .muted {
        color: #8a7e70;
        font-size: 13px;
        line-height: 1.5;
      }
      .error {
        background: #fff1f1;
        border: 1px solid #efc3c7;
        color: #7a2d35;
        padding: 14px;
        border-radius: 12px;
        font-family: "SFMono-Regular", "Menlo", "Consolas", monospace;
        font-size: 12px;
        white-space: pre-wrap;
      }
      .footer {
        margin-top: 24px;
        padding-top: 18px;
        border-top: 1px solid #ebe3d6;
        font-size: 12px;
        color: #8b8374;
      }
      @media (max-width: 640px) {
        .content {
          padding: 20px;
        }
        .header {
          padding: 24px 20px 18px;
        }
        .header h1 {
          font-size: 28px;
        }
        .label,
        .summary-grid td {
          display: block;
          width: auto;
        }
      }
    </style>
  </head>
  <body>
    <div class="wrap">
      <div class="card">
        <div class="header">
          <h1>Javadoc Deployment Status</h1>
          <p>
            Deployment report for the Cloudflare Pages Javadoc publish pipeline.
          </p>
        </div>
        <div class="content">
          <div class="hero-row">
            <span class="status-badge">${success?string("Success", "Failure")}</span>
          </div>

          <table class="summary-grid" role="presentation">
            <tr>
              <td class="label">Request ID</td>
              <td class="value">
                <#if requestId?? && requestId?has_content>
                  <span class="code">${requestId}</span>
                <#else>
                  N/A
                </#if>
              </td>
            </tr>
            <tr>
              <td class="label">Correlation ID</td>
              <td class="value">
                <#if correlationId?? && correlationId?has_content>
                  <span class="code">${correlationId}</span>
                <#else>
                  N/A
                </#if>
              </td>
            </tr>
            <tr>
              <td class="label">Site URL</td>
              <td class="value">
                <#if siteUrl?? && siteUrl?has_content>
                  <a href="${siteUrl}">${siteUrl}</a>
                <#else>
                  N/A
                </#if>
              </td>
            </tr>
          </table>

          <#if packages?? && packages?size gt 0>
            <div class="section-title">Input Packages</div>
            <div class="chip-block">
              <#list packages as pkg>
                <span class="artifact-chip">${pkg.groupId}:${pkg.artifactId}</span>
              </#list>
            </div>
          </#if>

          <#if artifactsByPackage?? && artifactsByPackage?size gt 0>
            <div class="section-title">Resolved Artifacts</div>
            <#list artifactsByPackage?keys as packageName>
              <div class="package-card">
                <div class="package-card-header">
                  <div class="package-name">${packageName}</div>
                </div>
                <div class="package-card-body">
                <#assign packageArtifacts = artifactsByPackage[packageName]>
                <#if packageArtifacts?size gt 0>
                  <div>
                    <#list packageArtifacts as artifact>
                      <span class="artifact-chip">${artifact}</span>
                    </#list>
                  </div>
                <#else>
                  <div class="muted">No artifacts were resolved for this package.</div>
                </#if>
              </div>
              </div>
            </#list>
          </#if>

          <#if !success && errorMessage??>
            <div class="section-title">Error</div>
            <div class="error">${errorMessage}</div>
          </#if>

          <div class="footer">
            Generated by cloudflare-javadoc-deploy
          </div>
        </div>
      </div>
    </div>
  </body>
</html>
