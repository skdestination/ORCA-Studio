import { registerPlugin, Capacitor } from "@capacitor/core";

export interface CrashlyticsPluginType {
  log(options: { message: string }): Promise<void>;
  setCustomKey(options: { key: string; value: string }): Promise<void>;
  setUserId(options: { userId: string }): Promise<void>;
  recordException(options: { message: string }): Promise<void>;
  crash(): Promise<void>;
}

const NativeCrashlytics = registerPlugin<CrashlyticsPluginType>("Crashlytics");

export class Crashlytics {
  static isNative = Capacitor.isNativePlatform();

  /**
   * Logs a message that will be included in the next crash report.
   */
  static async log(message: string): Promise<void> {
    if (this.isNative) {
      try {
        await NativeCrashlytics.log({ message });
      } catch (e) {
        console.warn("[Crashlytics Native Error] Failed to log:", e);
      }
    } else {
      console.log(`%c[Crashlytics Web] Logged message: ${message}`, "color: #ff9800; font-weight: bold;");
    }
  }

  /**
   * Sets a custom key/value pair to associate with crash reports.
   */
  static async setCustomKey(key: string, value: string | number | boolean): Promise<void> {
    const valStr = String(value);
    if (this.isNative) {
      try {
        await NativeCrashlytics.setCustomKey({ key, value: valStr });
      } catch (e) {
        console.warn("[Crashlytics Native Error] Failed to set custom key:", e);
      }
    } else {
      console.log(`%c[Crashlytics Web] Custom key set: ${key} = ${valStr}`, "color: #03a9f4; font-weight: bold;");
    }
  }

  /**
   * Sets a user ID to associate with crash reports.
   */
  static async setUserId(userId: string): Promise<void> {
    if (this.isNative) {
      try {
        await NativeCrashlytics.setUserId({ userId });
      } catch (e) {
        console.warn("[Crashlytics Native Error] Failed to set user ID:", e);
      }
    } else {
      console.log(`%c[Crashlytics Web] User ID set: ${userId}`, "color: #e91e63; font-weight: bold;");
    }
  }

  /**
   * Records a non-fatal exception.
   */
  static async recordException(message: string): Promise<void> {
    if (this.isNative) {
      try {
        await NativeCrashlytics.recordException({ message });
      } catch (e) {
        console.warn("[Crashlytics Native Error] Failed to record exception:", e);
      }
    } else {
      console.error(`%c[Crashlytics Web] Recorded Non-Fatal Exception: ${message}`, "background: #222; color: #ff5722; font-weight: bold; padding: 4px;");
    }
  }

  /**
   * Triggers a native crash (useful for testing/verifying the setup in the Firebase console).
   */
  static async crash(): Promise<void> {
    if (this.isNative) {
      try {
        await NativeCrashlytics.crash();
      } catch (e) {
        console.warn("[Crashlytics Native Error] Failed to trigger crash:", e);
      }
    } else {
      console.warn("%c[Crashlytics Web] Crash triggered (Simulated browser error)", "background: #f44336; color: white; font-weight: bold; padding: 6px;");
      throw new Error("Crashlytics Test Crash: Web Fallback Simulation triggered.");
    }
  }
}
