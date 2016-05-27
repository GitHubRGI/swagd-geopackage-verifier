package com.rgi.verifiertool;

import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

import javafx.beans.binding.DoubleBinding;
import javafx.beans.value.ChangeListener;
import javafx.concurrent.Task;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TitledPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import javafx.scene.effect.DropShadow;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;

import com.rgi.common.util.functional.ThrowingConsumer;
import com.rgi.geopackage.GeoPackage;
import com.rgi.geopackage.GeoPackage.OpenMode;
import com.rgi.geopackage.verification.VerificationIssue;
import com.rgi.geopackage.verification.VerificationLevel;

/**
 * Pane representing a single GeoPackage file's verification output
 *
 * @author Luke Lambert
 * @author Jenifer Cochran
 *
 */
public class FileVerificationPane extends TitledPane
{
    private final VBox content = new VBox(7);
    private final ContextMenu deleteMenu = new ContextMenu();
    private VBox parent;
    private final HashMap<String, Collection<VerificationIssue>> fileErrorMessages = new HashMap<>();
    private final ToggleButton    copyButton    = new ToggleButton("Copy Issues");


    /**
     * Constructor
     *
     * @param geoPackageFile
     *             File handle to a GeoPackage file
     */
    public FileVerificationPane(final File geoPackageFile)
    {
        if(geoPackageFile == null || !geoPackageFile.canRead())
        {
            throw new IllegalArgumentException("GeoPackage file may not be null, and must be a valid filename");
        }
        this.setText(geoPackageFile.getName());
        this.setPrettyTitle();//sets the font, fill, and style
        this.createTitleAndButton();//creates the top pane graphic with title
        this.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);//anchor pane is the only thing on top

        //create context menu to delete files from pane
        this.createContextMenu();
        this.setOnMousePressed(e -> this.createDeleteListener(e));
        //set the subsystem error messages/passing
        this.setContent(this.content);
        this.content.setStyle(String.format("-fx-background-color: %s;", Style.greyBlue.getHex()));
        this.copyButton.setVisible(false);//only show if there are errors

        final List<SubsystemVerificationPane> subsystems = Arrays.asList(new SubsystemVerificationPane("Core",       (geoPackage) -> geoPackage.core()      .getVerificationIssues(geoPackage.getFile(), VerificationLevel.Full)),
                                                                         //new SubsystemVerificationPane("Features",   (geoPackage) -> Collections.emptyList()),
                                                                         new SubsystemVerificationPane("Tiles",      (geoPackage) -> geoPackage.tiles()     .getVerificationIssues(VerificationLevel.Full)),
                                                                         new SubsystemVerificationPane("Extensions", (geoPackage) -> geoPackage.extensions().getVerificationIssues(VerificationLevel.Full)),
                                                                         new SubsystemVerificationPane("Schema",     (geoPackage) -> geoPackage.schema()    .getVerificationIssues(VerificationLevel.Full)),
                                                                         new SubsystemVerificationPane("Metadata",   (geoPackage) -> geoPackage.metadata()  .getVerificationIssues(VerificationLevel.Full)));

        this.content.getChildren().addAll(subsystems);


        final Task<ToggleButton> mainTask = new Task<ToggleButton>()
                                             {
                                                 @Override
                                                 protected ToggleButton call() throws Exception
                                                 {
                                                     try(final GeoPackage geoPackage = new GeoPackage(geoPackageFile, VerificationLevel.None, OpenMode.Open))
                                                     {
                                                         final List<Thread> updateThreads = subsystems.stream()
                                                                                                      .map(subsystem -> new Thread(FileVerificationPane.createTask(subsystem, geoPackage, FileVerificationPane.this.fileErrorMessages)))
                                                                                                      .collect(Collectors.toList());

                                                         updateThreads.forEach(thread -> thread.start());

                                                         updateThreads.forEach((ThrowingConsumer<Thread>)(thread -> thread.join()));
                                                         this.updateValue(FileVerificationPane.this.copyButton);
                                                     }
                                                     catch(final Exception ex)
                                                     {
                                                         throw new RuntimeException(ex);
                                                     }

                                                     return FileVerificationPane.this.copyButton;
                                                 }
                                             };
        mainTask.valueProperty().addListener((ChangeListener<ToggleButton>)(observable, oldValue, newValue) -> {
            if(!this.fileErrorMessages.isEmpty())
            {
                newValue.setVisible(true);
            }
            else
            {
                newValue.setVisible(false);
            }
        });
        final Thread mainThread = new Thread(mainTask);
        mainThread.start();
    }

    private void createTitleAndButton()
    {
        //this sets the text to the left and the copy button to the right in the titled pane
        final Text fileTitle = new Text();
        //binds the text position and other properties to the anchor pane
        fileTitle.textProperty().bind(this.textProperty());
        fileTitle.fillProperty().bind(this.textFillProperty());
        fileTitle.fontProperty().bind(this.fontProperty());
        fileTitle.setLayoutY(this.getLayoutY() + 20);
        //file name left copy button right
        final AnchorPane title = new AnchorPane();
        AnchorPane.setLeftAnchor(fileTitle, 1.0);
        AnchorPane.setRightAnchor(this.createCopyButton(), 0.0);

        title.getChildren().addAll(fileTitle, this.copyButton);
        //add the anchor pane to the titled pane
        this.setGraphic(title);
        //allow the buttons to move with the resizing of the window
        title.prefWidthProperty().bind(new DoubleBinding() {
            {
                super.bind(VerifierMainWindow.getRootWidthProperty());
            }

            @Override
            protected double computeValue() {
                final double breathingSpace = 90 ;
                final double value = VerifierMainWindow.getRootWidth()- breathingSpace ;
                return value;
            }
        });
    }

    private ToggleButton createCopyButton()
    {
        this.copyButton.setMaxSize(109, 30);
        this.copyButton.setMinSize(109, 30);
        this.copyButton.setStyle(String.format("-fx-border-radius: 5;"
                                             + "-fx-background-radius: 5;"
                                             + "-fx-background-color: linear-gradient(%s, %s); ",
                                               Style.brightBlue.getHex(),
                                               Style.darkAquaBlue.getHex()));

        this.copyButton.setTextFill(Style.white.toColor());
        this.copyButton.setFont(Font.font(Style.getMainFont(), FontWeight.BOLD, 12));

        final Tooltip copyMessage = new Tooltip("Copy Failed Requirements for this File");
        Tooltip.install(this.copyButton, copyMessage);
        //set actions to button
        this.copyButton.setOnMouseEntered (e-> this.copyButton.setEffect(new DropShadow()));
        this.copyButton.setOnMouseExited  (e-> this.copyButton.setEffect(null));
        this.copyButton.setOnMousePressed (e-> this.copyButton.setStyle(String.format(" -fx-background-color: linear-gradient(%s, %s)",  Style.darkAquaBlue.getHex(), Style.brightBlue.getHex())));
        this.copyButton.setOnMouseReleased(e-> this.copyButton.setStyle(String.format(" -fx-background-color: linear-gradient(%s, %s)",  Style.brightBlue.getHex(),   Style.darkAquaBlue.getHex())));
        this.copyButton.setOnAction(e-> {
                                            final Clipboard clipboard = Clipboard.getSystemClipboard();
                                            final ClipboardContent clipboardContent = new ClipboardContent();
                                            clipboard.clear();
                                            clipboardContent.clear();
                                            clipboardContent.putString(this.getVerificationIssues());
                                            clipboard.setContent(clipboardContent);
                                        });

        return this.copyButton;
    }

    private String getVerificationIssues()
    {
        final String header = String.format("SWAGD GeoPackage Verifier Tool Version %s.\nGeoPackage Encoding Standard Specification Version %s.\nFile: %s\n\n\n",
                                      VerifierMainWindow.rgiToolVersionNumber,
                                      VerifierMainWindow.geoPackageSpecificationVersionNumber,
                                      this.getText());

        final String body = this.fileErrorMessages.keySet().stream()
                                                     .sorted((subsystem1, susbystem2) -> compareSubsystems(subsystem1, susbystem2))
                                                     .map(subsystem -> {
                                                                           return getVerificationIssuesForSubsystem(subsystem, this.fileErrorMessages.get(subsystem));
                                                                       })
                                                     .collect(Collectors.joining("\n"));
        return String.format("%s\n%s", header, body);
    }

    private static Integer compareSubsystems(final String subsystem1, final String susbystem2)
    {
        final int firstSubInt = findSubsystemValue(subsystem1);
        final int seconSubInt = findSubsystemValue(susbystem2);

        return Integer.compare(firstSubInt, seconSubInt);
    }

    private static int findSubsystemValue(final String subsystem1)
    {
        switch(subsystem1)
        {
            case "Core":       return 0;
            case "Tiles":      return 1;
            case "Extensions": return 2;
            case "Schema" :    return 3;
            case "Metadata" :  return 4;

            default: return 5;
        }
    }

    private static String getVerificationIssuesForSubsystem(final String subsystemName, final Collection<VerificationIssue> issues)
    {
          final String failedMessages = issues.stream()
                                              .sorted((issue1, issue2) -> issue1.getRequirement()
                                                                                .reference()
                                                                                .compareTo(issue2.getRequirement()
                                                                                                 .reference()))
                                              .map(issue -> { return String.format("\t\r(%s) %s: \"%s\"\n\n\t\r%s\n",
                                                                                   issue.getSeverity(),
                                                                                   issue.getRequirement().reference(),
                                                                                   issue.getRequirement().text(),
                                                                                   issue.getReason());
                                                            })
                                              .collect(Collectors.joining("\n"));

         return  String.format("%s Issues:\n\n%s",
                                subsystemName,
                                failedMessages);
    }

    private void createDeleteListener(final MouseEvent e)
    {
        if(e.isSecondaryButtonDown())
        {
            this.deleteMenu.show(this, e.getScreenX(), e.getScreenY());
        }
    }

    /**
     * @param parent the parent that this pane is added to
     */
    public void setParent(final VBox parent)
    {
        this.parent = parent;
    }

    private void createContextMenu()
    {
        final MenuItem remove = new MenuItem("Remove");
        final MenuItem cancel = new MenuItem("Cancel");
        this.deleteMenu.getItems().addAll(remove, cancel);

        remove.setOnAction(e -> {
                                if(this.parent != null)
                                  {
                                      this.parent.getChildren().remove(this);
                                  }
                                });
    }

    private  void setPrettyTitle()
    {
        this.setTextFill(Style.brightBlue.toColor());
        this.setFont(Font.font(Style.getMainFont(), FontWeight.BOLD, 18));
        this.setStyle(String.format("-fx-body-color: %s;", Style.white.getHex()));
    }

    private static Task<Collection<VerificationIssue>> createTask(final SubsystemVerificationPane subsystemVerificationPane, final GeoPackage geoPackage, final HashMap<String, Collection<VerificationIssue>> fileErrorMessages2)
    {
        final Task<Collection<VerificationIssue>> task = new Task<Collection<VerificationIssue>>()
                                                         {
                                                             @Override
                                                             protected Collection<VerificationIssue> call() throws Exception
                                                             {
                                                                 try
                                                                 {
                                                                     final Collection<VerificationIssue> messages = subsystemVerificationPane.getIssues(geoPackage);
                                                                     if(!messages.isEmpty())
                                                                     {
                                                                         fileErrorMessages2.put(subsystemVerificationPane.getName(), messages);
                                                                     }
                                                                     this.updateValue(messages);
                                                                     return messages;
                                                                 }
                                                                 catch(final Exception ex)
                                                                 {
                                                                     return null;
                                                                 }

                                                             }
                                                         };

        task.valueProperty().addListener((ChangeListener<Collection<VerificationIssue>>)(observable, oldValue, newValue) -> subsystemVerificationPane.update(newValue));

        return task;
    }

}
