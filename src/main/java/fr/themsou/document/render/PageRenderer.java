package fr.themsou.document.render;

import fr.themsou.document.editions.Edition;
import fr.themsou.document.editions.elements.Element;
import fr.themsou.document.editions.elements.NoteElement;
import fr.themsou.document.editions.elements.TextElement;
import fr.themsou.main.Main;
import fr.themsou.panel.leftBar.notes.NoteTreeItem;
import fr.themsou.panel.leftBar.notes.NoteTreeView;
import fr.themsou.panel.leftBar.texts.LBTextTab;
import fr.themsou.panel.leftBar.texts.TextTreeItem;
import fr.themsou.utils.Builders;
import fr.themsou.utils.CallBack;
import javafx.application.Platform;
import javafx.beans.value.ObservableValue;
import javafx.embed.swing.SwingFXUtils;
import javafx.event.EventHandler;
import javafx.geometry.VPos;
import javafx.scene.Cursor;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.CustomMenuItem;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.scene.shape.Shape;
import javafx.scene.text.Text;
import javafx.scene.text.TextBoundsType;
import org.apache.pdfbox.pdmodel.common.PDRectangle;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

public class PageRenderer extends Pane {

    PageStatus status = PageStatus.HIDE;

    private ImageView renderView;
    private int page;
    private ArrayList<Element> elements = new ArrayList<>();
    private double mouseX = 0;
    private double mouseY = 0;

    private ProgressBar loader = new ProgressBar();
    ContextMenu menu = new ContextMenu();

    public PageRenderer(int page){
        this.page = page;
        setStyle("-fx-background-color: white;");

        // LOADER
        loader.setPrefWidth(300);
        loader.setPrefHeight(20);
        loader.translateXProperty().bind(widthProperty().divide(2).subtract(loader.widthProperty().divide(2)));
        loader.translateYProperty().bind(heightProperty().divide(2).subtract(loader.heightProperty().divide(2)));
        loader.setVisible(false);
        getChildren().add(loader);

        // BINDINGS & SIZES SETUP
        PDRectangle pageSize = Main.mainScreen.document.pdfPagesRender.getPageSize(page);
        final double ratio = pageSize.getHeight() / pageSize.getWidth();

        setWidth(Main.mainScreen.getPageWidth());
        setHeight(Main.mainScreen.getPageWidth() * ratio);

        setMaxWidth(Main.mainScreen.getPageWidth());
        setMinWidth(Main.mainScreen.getPageWidth());

        setMaxHeight(Main.mainScreen.getPageWidth() * ratio);
        setMinHeight(Main.mainScreen.getPageWidth() * ratio);

        // BORDER
        DropShadow ds = new DropShadow();
        ds.setColor(Color.BLACK);
        setEffect(ds);

        // UPDATE MOUSE COORDINATES
        setOnMouseMoved(e -> {
            mouseX = e.getX();
            mouseY = e.getY();
        });
        setOnMouseDragged(e -> {

            mouseX = e.getX();
            mouseY = e.getY();
        });
        setOnMouseEntered(event -> Main.mainScreen.document.setCurrentPage(page));

        // Show Status
        Main.mainScreen.pane.translateYProperty().addListener((ObservableValue<? extends Number> observable, Number oldValue, Number newValue) -> {
            updateShowStatus();
        });


        Builders.setMenuSize(menu);


        setOnMousePressed(e -> {
            e.consume();

            Main.mainScreen.setSelected(null);
            Main.lbNoteTab.treeView.getSelectionModel().select(null);
            menu.hide();
            menu.getItems().clear();
            if(e.getButton() == MouseButton.SECONDARY){

                NoteTreeView.defineNaNLocations();
                NoteTreeItem note = NoteTreeView.getNextNote(page, (int) e.getY());
                if(note != null) menu.getItems().add(new CustomMenuItem(note.getEditGraphics((int) Main.lbTextTab.treeView.getWidth()-50)));

                List<TextTreeItem> mostUsed = LBTextTab.getMostUseElements();
                for(int i = 0; i <= 3; i++){
                    TextTreeItem item = mostUsed.get(i);

                    Pane pane = new Pane();

                    Pane sub = new Pane();

                    Text name = new Text(item.name.getText());
                    name.setTextOrigin(VPos.TOP);
                    name.setLayoutY(3);
                    name.setFont(item.name.getFont());
                    name.setFill(item.name.getTextFill());

                    sub.setOnMouseClicked(event -> item.addToDocument(false));

                    sub.setLayoutY(-6);
                    sub.setPrefHeight(name.getLayoutBounds().getHeight()+7);
                    sub.setPrefWidth(Math.max(name.getLayoutBounds().getWidth(), Main.lbTextTab.treeView.getWidth() - 50));

                    pane.setPrefHeight(name.getLayoutBounds().getHeight()+7-14);

                    sub.getChildren().add(name);
                    pane.getChildren().add(sub);

                    CustomMenuItem menuItem = new CustomMenuItem(pane);
                    menu.getItems().add(menuItem);
                }




                menu.show(this, e.getScreenX(), e.getScreenY());
            }
        });

    }

    public void updateShowStatus(){

        int firstTest = getShowStatus();
        Platform.runLater(() -> {
            if(firstTest == getShowStatus()) switchVisibleStatus(firstTest);
        });

    }
    public int getShowStatus(){ // 0 : Visible | 1 : Hide | 2 : Hard Hide
        int pageHeight = (int) (getHeight()*Main.mainScreen.pane.getScaleX());
        int upDistance = (int) (Main.mainScreen.pane.getTranslateY() - Main.mainScreen.zoomOperator.getPaneShiftY() + getTranslateY()*Main.mainScreen.pane.getScaleX() + pageHeight);
        int downDistance = (int) (Main.mainScreen.pane.getTranslateY() - Main.mainScreen.zoomOperator.getPaneShiftY() + getTranslateY()*Main.mainScreen.pane.getScaleX());

        if((upDistance + pageHeight) > 0 && (downDistance - pageHeight) < Main.mainScreen.getHeight()){
            return 0;
        }else{
            if((upDistance + pageHeight*10) < 0 || (downDistance - pageHeight*10) > Main.mainScreen.getHeight()) return 2;
            return 1;
        }
    }
    private void switchVisibleStatus(int showStatus){
        if(showStatus == 0){
            setVisible(true);
            if(status == PageStatus.HIDE) render();
        }else if(showStatus >= 1){

            setVisible(false);
            if(showStatus == 2){
                getChildren().remove(renderView); status = PageStatus.HIDE;
                for(Node node : getChildren()) node.setVisible(false);
            }

        }

    }
    private void render(){
        status = PageStatus.RENDERING;
        loader.setVisible(true);
        setCursor(Cursor.WAIT);

        Main.mainScreen.document.pdfPagesRender.renderPage(page, new CallBack<>() {
            @Override public void call(BufferedImage image){

                if(image == null){
                    status = PageStatus.FAIL;
                    return;
                }

                renderView = new ImageView(SwingFXUtils.toFXImage(image, null));

                renderView.fitHeightProperty().bind(heightProperty());
                renderView.fitWidthProperty().bind(widthProperty());

                for(Node node : getChildren()){
                    node.setVisible(true);
                }

                setCursor(Cursor.DEFAULT);
                loader.setVisible(false);
                getChildren().add(0, renderView);
                status = PageStatus.RENDERED;
            }
        });
    }

    public void clearElements(){
        getChildren().clear();
        if(status == PageStatus.RENDERED){
            getChildren().add(renderView);
        }
        elements = new ArrayList<>();
    }

    public void switchElementPage(Element element, PageRenderer page){

        if(element != null){

            elements.remove(element);
            getChildren().remove(element);

            element.setPage(page);

            page.elements.add(element);
            page.getChildren().add((Shape) element);
        }
    }
    public void addElementSimple(Element element){

        if(element != null){
            elements.add(element);
            getChildren().add((Shape) element);

            if(element instanceof NoteElement){
                Main.lbNoteTab.treeView.addElement((NoteElement) element);
            }

            if(status != PageStatus.RENDERED){
                ((Shape) element).setVisible(false);
            }
        }
    }
    public void addElement(Element element, boolean update){

        if(element != null){

            elements.add(element);
            getChildren().add((Shape) element);
            Edition.setUnsave();

            if(element instanceof TextElement){
                if(update) Main.lbTextTab.addOnFileElement((TextElement) element);
            }else if(element instanceof NoteElement){
                Main.lbNoteTab.treeView.addElement((NoteElement) element);
            }

            if(status != PageStatus.RENDERED) ((Shape) element).setVisible(false);

        }
    }
    public void removeElement(Element element, boolean update){

        if(element != null){
            elements.remove(element);
            getChildren().remove((Shape) element);
            Edition.setUnsave();

            if(element instanceof TextElement){
                if(update) Main.lbTextTab.removeOnFileElement((TextElement) element);
            }else if(element instanceof NoteElement){
                Main.lbNoteTab.treeView.removeElement((NoteElement) element);
            }

        }
    }

    public double getMouseX(){
        return Math.max(Math.min(mouseX, getWidth()), 0);
    }
    public double getMouseY(){
        return Math.max(Math.min(mouseY, getWidth()), 0);
    }

    public int getPage() {
        return page;
    }
    public ArrayList<Element> getElements() {
        return elements;
    }
}
