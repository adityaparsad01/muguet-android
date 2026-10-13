// Platform fallback for builds without Apple's GameController framework.
// Android controller integration can be added without linking Objective-C APIs.
use std::sync::atomic::{AtomicBool, Ordering};

pub static ACTIVE: AtomicBool = AtomicBool::new(false);
pub static FOCUSED: AtomicBool = AtomicBool::new(true);
pub static IN_VEHICLE: AtomicBool = AtomicBool::new(false);
pub static HORN_PAD: AtomicBool = AtomicBool::new(false);
pub static HORN_TOUCH: AtomicBool = AtomicBool::new(false);

pub fn horn_held() -> bool {
    HORN_PAD.load(Ordering::Relaxed) || HORN_TOUCH.load(Ordering::Relaxed)
}

pub fn poll() -> bool {
    false
}

pub fn release_all() {
    ACTIVE.store(false, Ordering::Relaxed);
    HORN_PAD.store(false, Ordering::Relaxed);
    HORN_TOUCH.store(false, Ordering::Relaxed);
}
