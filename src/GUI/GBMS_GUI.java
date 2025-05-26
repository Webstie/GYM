package GUI;

import Client.MeetingScheduler;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;

import java.net.InetAddress;
import java.util.Arrays;
import java.util.List;

public class GBMS_GUI extends Application {

    private TextField activityField;
    private DatePicker datePicker;
    private TextField timeField;
    private TextArea ipField;
    private Spinner<Integer> minParticipantsSpinner;
    private TextArea console;
    private MeetingScheduler scheduler;
    private static int bookingIdCounter = 100;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("Gym Booking Management System");

        // 初始化调度器
        try {
            InetAddress serverIP = InetAddress.getByName("127.0.0.1");
            int clientPort = 9877;
            int serverPort = 9876;

            scheduler = new MeetingScheduler(clientPort, serverPort, serverIP);
            scheduler.addMessageListener(msg -> Platform.runLater(() -> {
                if (msg == null) {
                    console.appendText("[null message received]\n");
                } else {
                    console.appendText(msg + "\n");
                }
            }));
            scheduler.start();
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }

        console = new TextArea();
        console.setEditable(false);
        console.setPrefHeight(200);

        TabPane tabPane = new TabPane();
        tabPane.getTabs().addAll(
                new Tab("预约活动", buildBookingTab()),
                new Tab("取消活动", buildCancelTab())
        );
        tabPane.getTabs().forEach(tab -> tab.setClosable(false));

        VBox root = new VBox(tabPane, new Label("控制台日志:"), console);
        root.setPadding(new Insets(10));
        root.setSpacing(10);

        primaryStage.setScene(new Scene(root, 550, 550));
        primaryStage.show();
    }

    private VBox buildBookingTab() {
        activityField = new TextField();
        timeField = new TextField();
        ipField = new TextArea();
        datePicker = new DatePicker();
        minParticipantsSpinner = new Spinner<>(1, 100, 2);

        Button sendButton = new Button("发送 BOOK 请求");
        sendButton.setOnAction(e -> sendBookingRequest());

        GridPane form = new GridPane();
        form.setPadding(new Insets(10));
        form.setVgap(8);
        form.setHgap(10);
        form.add(new Label("活动类型:"), 0, 0);
        form.add(activityField, 1, 0);
        form.add(new Label("日期:"), 0, 1);
        form.add(datePicker, 1, 1);
        form.add(new Label("时间(HH:MM):"), 0, 2);
        form.add(timeField, 1, 2);
        form.add(new Label("参与者 IP（逗号分隔）:"), 0, 3);
        form.add(ipField, 1, 3);
        form.add(new Label("最小参与人数:"), 0, 4);
        form.add(minParticipantsSpinner, 1, 4);
        form.add(sendButton, 1, 5);

        VBox layout = new VBox(10, form);
        layout.setPadding(new Insets(10));
        return layout;
    }

    private VBox buildCancelTab() {
        TextField meetingIdField = new TextField();
        meetingIdField.setPromptText("例如：MT#2");

        Button cancelButton = new Button("发送 CANCEL 请求");
        cancelButton.setOnAction(e -> {
            String meetingID = meetingIdField.getText().trim();
            if (meetingID.isEmpty()) {
                console.appendText("⚠️ Meeting ID 不能为空\n");
                return;
            }
            sendCancelRequest(meetingID);
        });

        VBox cancelLayout = new VBox(10, new Label("取消会议 ID:"), meetingIdField, cancelButton);
        cancelLayout.setPadding(new Insets(10));
        return cancelLayout;
    }

    private void sendBookingRequest() {
        String activity = activityField.getText().trim();
        String date = datePicker.getValue() != null ? datePicker.getValue().toString() : "";
        String time = timeField.getText().trim();
        List<String> ips = Arrays.asList(ipField.getText().trim().split(","));
        int min = minParticipantsSpinner.getValue();

        if (activity.isEmpty() || date.isEmpty() || time.isEmpty() || ips.isEmpty()) {
            console.appendText("⚠️ 请填写所有字段\n");
            return;
        }

        String requestId = "RQ#" + (bookingIdCounter++);
        scheduler.sendBookRequest(requestId, date, time, activity, ips, min);
    }

    public void sendCancelRequest(String meetingId) {
        new Thread(() -> {
            scheduler.sendCancelRequest(meetingId); // 使用 MeetingScheduler 封装的方法

            Platform.runLater(() -> {
                console.appendText("📤 已请求取消会议: " + meetingId + "\n");
            });
        }).start();
    }
}
