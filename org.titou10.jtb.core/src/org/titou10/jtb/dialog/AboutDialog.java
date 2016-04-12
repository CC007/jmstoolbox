/* Copyright (C) 2015 Denis Forveille titou10.titou10@gmail.com
 * 
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License along with this program. If not, see
 * <http://www.gnu.org/licenses/>. */
package org.titou10.jtb.dialog;

import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.program.Program;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Link;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.wb.swt.SWTResourceManager;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.Version;
import org.titou10.jtb.util.Utils;

/**
 * About Dialog
 * 
 * @author Denis Forveille
 *
 */
public class AboutDialog extends Dialog {

   private static final String TITLE         = "Universal JMS Browser";
   private static final String AUTHOR        = "Author: Denis Forveille";
   private static final String CONTRIBUTOR   = "Contributors: Yannick Beaudoin";
   private static final String VERSION       = "Version %d.%d.%d";
   private static final String EMAIL         = "titou10.titou10@gmail.com";
   private static final String EMAIL_MAILTO  = "mailto:" + EMAIL;
   private static final String EMAIL_LINK    = "<a href=\"" + EMAIL_MAILTO + "\">" + EMAIL + "</a>";
   private static final String WEB           = "https://sourceforge.net/p/jmstoolbox";
   private static final String WEB_LINK      = "<a href=\"" + WEB + "\">" + WEB + "</a>";
   private static final String WEB_WIKI      = WEB + "/wiki/Home/";
   private static final String WEB_WIKI_LINK = "<a href=\"" + WEB_WIKI + "\">" + WEB_WIKI + "</a>";

   private static final String LOGO          = "icons/branding/logo-jmstoolbox.jpg";

   public AboutDialog(Shell parentShell) {
      super(parentShell);
      setShellStyle(SWT.CLOSE | SWT.TITLE | SWT.PRIMARY_MODAL);
   }

   @Override
   protected void configureShell(Shell newShell) {
      super.configureShell(newShell);
      newShell.setText("About");
   }

   @Override
   protected Control createDialogArea(Composite parent) {

      Version v = FrameworkUtil.getBundle(AboutDialog.class).getVersion();
      String version = String.format(VERSION, v.getMajor(), v.getMinor(), v.getMicro());

      Composite container = (Composite) super.createDialogArea(parent);
      container.setFont(SWTResourceManager.getFont("Tahoma", 12, SWT.NORMAL));
      GridLayout gl_container = new GridLayout(1, false);
      gl_container.marginWidth = 20;
      gl_container.verticalSpacing = 10;
      container.setLayout(gl_container);

      Label lblTitle = new Label(container, SWT.NONE);
      GridData gd_lblTitle = new GridData(SWT.CENTER, SWT.CENTER, true, false, 1, 1);
      gd_lblTitle.verticalIndent = 20;
      lblTitle.setLayoutData(gd_lblTitle);
      lblTitle.setFont(SWTResourceManager.getFont("Verdana", 20, SWT.BOLD));
      lblTitle.setAlignment(SWT.CENTER);
      lblTitle.setText(TITLE);

      Label lblImage = new Label(container, SWT.BORDER);
      GridData gd_lblImage = new GridData(SWT.CENTER, SWT.CENTER, true, false, 1, 1);
      gd_lblImage.verticalIndent = 20;
      lblImage.setLayoutData(gd_lblImage);
      lblImage.setAlignment(SWT.CENTER);
      lblImage.setText("Image");
      lblImage.setImage(Utils.getImage(this.getClass(), LOGO));

      Composite authorComposite = new Composite(container, SWT.NONE);
      authorComposite.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, true, false, 1, 1));
      authorComposite.setLayout(new GridLayout(2, false));

      Label lblAuthor = new Label(authorComposite, SWT.NONE);
      lblAuthor.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, false, false, 1, 1));
      lblAuthor.setFont(SWTResourceManager.getFont("Tahoma", 10, SWT.NORMAL));
      lblAuthor.setText(AUTHOR);

      Link emailLink = new Link(authorComposite, SWT.NONE);
      emailLink.setFont(SWTResourceManager.getFont("Tahoma", 9, SWT.NORMAL));
      emailLink.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, false, false, 1, 1));
      emailLink.setText(EMAIL_LINK);
      emailLink.addSelectionListener(new SelectionAdapter() {
         @Override
         public void widgetSelected(SelectionEvent e) {
            Program.launch(EMAIL_MAILTO);
         }
      });

      Label lblHelper = new Label(container, SWT.NONE);
      lblHelper.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, true, false, 1, 1));
      lblHelper.setFont(SWTResourceManager.getFont("Tahoma", 9, SWT.NORMAL));
      lblHelper.setText(CONTRIBUTOR);

      Composite webContainer = new Composite(container, SWT.NONE);
      webContainer.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, true, false, 1, 1));
      webContainer.setLayout(new GridLayout(2, false));

      Label lblWeb = new Label(webContainer, SWT.NONE);
      lblWeb.setText("Web Site:");

      Link webLink = new Link(webContainer, SWT.NONE);
      webLink.setFont(SWTResourceManager.getFont("Tahoma", 9, SWT.NORMAL));
      webLink.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, true, false, 1, 1));
      webLink.setText(WEB_LINK);
      webLink.addSelectionListener(new SelectionAdapter() {
         @Override
         public void widgetSelected(SelectionEvent e) {
            Program.launch(WEB);
         }
      });

      Composite wikiComposite = new Composite(container, SWT.NONE);
      wikiComposite.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, true, false, 1, 1));
      wikiComposite.setLayout(new GridLayout(2, false));

      Label lblWikiLink = new Label(wikiComposite, SWT.NONE);
      lblWikiLink.setText("Online Manual:");

      Link webWikiLink = new Link(wikiComposite, SWT.NONE);
      webWikiLink.setFont(SWTResourceManager.getFont("Tahoma", 9, SWT.NORMAL));
      webWikiLink.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, true, false, 1, 1));
      webWikiLink.setText(WEB_WIKI_LINK);
      webWikiLink.addSelectionListener(new SelectionAdapter() {
         @Override
         public void widgetSelected(SelectionEvent e) {
            Program.launch(WEB_WIKI);
         }
      });

      Label lblVersion = new Label(container, SWT.NONE);
      lblVersion.setFont(SWTResourceManager.getFont("Tahoma", 10, SWT.NORMAL));
      lblVersion.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, true, false, 1, 1));
      lblVersion.setAlignment(SWT.CENTER);
      lblVersion.setText(version);

      webWikiLink.setFocus();

      return container;
   }

   @Override
   protected void createButtonsForButtonBar(Composite parent) {
      createButton(parent, IDialogConstants.OK_ID, "OK", true);
   }

   @Override
   protected Control createButtonBar(Composite parent) {
      Composite composite = new Composite(parent, SWT.NONE);

      // create a layout with spacing and margins appropriate for the font size.
      GridLayout layout = new GridLayout();
      layout.numColumns = 0; // this is incremented by createButton
      layout.makeColumnsEqualWidth = true;
      layout.marginWidth = convertHorizontalDLUsToPixels(IDialogConstants.HORIZONTAL_MARGIN);
      layout.marginHeight = convertVerticalDLUsToPixels(IDialogConstants.VERTICAL_MARGIN);
      layout.horizontalSpacing = convertHorizontalDLUsToPixels(IDialogConstants.HORIZONTAL_SPACING);
      layout.verticalSpacing = convertVerticalDLUsToPixels(IDialogConstants.VERTICAL_SPACING);
      composite.setLayout(layout);

      // GridData data = new GridData(GridData.HORIZONTAL_ALIGN_END | GridData.VERTICAL_ALIGN_CENTER);
      GridData data = new GridData(GridData.HORIZONTAL_ALIGN_CENTER | GridData.VERTICAL_ALIGN_CENTER);
      composite.setLayoutData(data);
      composite.setFont(parent.getFont());

      // Add the buttons to the button bar.
      createButtonsForButtonBar(composite);
      return composite;
   }

}