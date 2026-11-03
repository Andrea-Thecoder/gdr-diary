<#ftl output_format="HTML">
<#import "template.ftl" as layout>
<@layout.emailLayout eyebrow=msg("gdrEmailPasswordResetEyebrow") title=msg("gdrEmailPasswordResetTitle") preheader=msg("gdrEmailPasswordResetText")>
<@layout.text>${msg("gdrEmailPasswordResetText")}</@layout.text>
<@layout.action href=link label=msg("gdrEmailPasswordResetButton") />
<@layout.note>${msg("gdrEmailExpires", linkExpirationFormatter(linkExpiration))}</@layout.note>
<@layout.note>${msg("gdrEmailPasswordResetIgnore")}</@layout.note>
</@layout.emailLayout>
