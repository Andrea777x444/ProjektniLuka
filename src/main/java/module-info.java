module ba.etf.luka {
    requires javafx.controls;
    requires javafx.fxml;
    requires java.logging;

    opens ba.etf.luka.admin.controller to javafx.fxml;
    opens ba.etf.luka.user.controller  to javafx.fxml;

    exports ba.etf.luka.admin;
    exports ba.etf.luka.user;
    exports ba.etf.luka.model;
}