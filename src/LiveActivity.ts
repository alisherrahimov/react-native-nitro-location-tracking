import type {
  LiveActivityContent,
  NitroLocationTracking,
} from './NitroLocationTracking.nitro';

type LiveActivityNative = Pick<
  NitroLocationTracking,
  | 'isLiveActivitySupported'
  | 'areLiveActivitiesEnabled'
  | 'isLiveActivityRunning'
  | 'startLiveActivityWithContent'
  | 'updateLiveActivityWithContent'
  | 'endLiveActivityWithContent'
>;

export interface LiveActivityEndOptions {
  /** Shown as the last state before the activity disappears. */
  finalContent?: LiveActivityContent;
  /** How long `finalContent` stays visible (default 0 = remove immediately). */
  dismissAfterSeconds?: number;
}

export interface LiveActivityController {
  /** iOS 16.2+ (Android: ongoing notification card, always true). */
  isSupported(): boolean;
  /** false when the user turned Live Activities / notifications off. */
  areEnabled(): boolean;
  /** true while an activity started by `start()` is alive (survives app restarts on iOS). */
  isRunning(): boolean;
  /** Ends any previous activity and starts a new one. Throws when disabled. */
  start(content: LiveActivityContent): void;
  /**
   * Replaces the whole content. Identical consecutive payloads are skipped
   * (ActivityKit rate-limits updates). Returns true when an update was sent.
   */
  update(content: LiveActivityContent): boolean;
  /** Ends every activity started by this library. */
  end(options?: LiveActivityEndOptions): void;
}

export function createLiveActivityController(
  native: LiveActivityNative
): LiveActivityController {
  let lastSent: string | null = null;

  return {
    isSupported: () => native.isLiveActivitySupported(),
    areEnabled: () => native.areLiveActivitiesEnabled(),
    isRunning: () => native.isLiveActivityRunning(),
    start(content) {
      native.startLiveActivityWithContent(content);
      lastSent = JSON.stringify(content);
    },
    update(content) {
      const serialized = JSON.stringify(content);
      if (serialized === lastSent) return false;
      native.updateLiveActivityWithContent(content);
      lastSent = serialized;
      return true;
    },
    end(options) {
      lastSent = null;
      native.endLiveActivityWithContent(
        options?.finalContent,
        options?.dismissAfterSeconds
      );
    },
  };
}
