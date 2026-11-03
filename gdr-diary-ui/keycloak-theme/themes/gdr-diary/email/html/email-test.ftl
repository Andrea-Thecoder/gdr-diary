<#ftl output_format="HTML">
<#import "template.ftl" as layout>
<@layout.emailLayout eyebrow=msg("gdrEmailTestEyebrow") title=msg("gdrEmailTestTitle") preheader=msg("gdrEmailTestText")>
<@layout.text>${msg("gdrEmailTestText")}</@layout.text>
</@layout.emailLayout>
