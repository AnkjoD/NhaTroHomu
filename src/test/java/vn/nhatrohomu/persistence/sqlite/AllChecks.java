package vn.nhatrohomu.persistence.sqlite;

/** Same test suite for Maven and the Windows build script. */
public final class AllChecks {
  private AllChecks() {}

  public static void main(String[] args) throws Exception {
    SelfTest.main(new String[0]);
    FeatureTest.main(new String[0]);
    RentalTest.main(new String[0]);
    SafetyTest.main(new String[0]);
    ReportTest.main(new String[0]);
    ProfileHistoryTest.main(new String[0]);
    HistoricalInvoiceTest.main(new String[0]);
    DebtFlowTest.main(new String[0]);
    if (java.awt.GraphicsEnvironment.isHeadless())
      System.out.println("SKIP: Swing checks require a desktop session.");
    else {
      DisplayTest.main(new String[0]);
      InvoiceWorkflowTest.main(new String[0]);
      GuiSmokeTest.main(new String[0]);
    }
  }
}
