// Simple Android touch-to-input mapping. Kept independent of UIKit/Objective-C.
// A visible on-screen overlay is intentionally a separate UI concern; this layer
// makes touch gestures usable while keeping the existing engine input protocol.
use crate::input;
use std::collections::HashMap;
use std::sync::{LazyLock, Mutex};
use winit::dpi::PhysicalSize;
use winit::event::{Touch, TouchPhase};

#[derive(Clone, Copy)]
enum Role {
    Move { origin: (f64, f64) },
    Look,
    Key(&'static str),
    Fire,
}
struct Finger {
    role: Role,
    last: (f64, f64),
}
static FINGERS: LazyLock<Mutex<HashMap<u64, Finger>>> =
    LazyLock::new(|| Mutex::new(HashMap::new()));

fn key(k: &str, down: bool) {
    input::key(k, down, false, None, false);
}
fn role_at(x: f64, y: f64, w: f64, h: f64) -> Role {
    if x < w * 0.40 && y > h * 0.48 {
        Role::Move { origin: (x, y) }
    } else if x > w * 0.78 && y > h * 0.78 {
        Role::Fire
    } else if x > w * 0.68 && y > h * 0.58 {
        Role::Key("Space")
    } else if x > w * 0.52 && x <= w * 0.68 && y > h * 0.79 {
        Role::Key("KeyF")
    } else if x < w * 0.16 && y < h * 0.16 {
        Role::Key("Escape")
    } else {
        Role::Look
    }
}

pub fn touch(t: &Touch, size: PhysicalSize<u32>) {
    let w = size.width.max(1) as f64;
    let h = size.height.max(1) as f64;
    let x = t.location.x;
    let y = t.location.y;
    let mut fingers = FINGERS.lock().unwrap();
    match t.phase {
        TouchPhase::Started => {
            let role = role_at(x, y, w, h);
            match role {
                Role::Key(k) => key(k, true),
                Role::Fire => input::button(0, true),
                _ => {}
            }
            fingers.insert(t.id, Finger { role, last: (x, y) });
        }
        TouchPhase::Moved => {
            if let Some(f) = fingers.get_mut(&t.id) {
                match f.role {
                    Role::Look => {
                        let dx = x - f.last.0;
                        let dy = y - f.last.1;
                        input::mouse_delta_f(dx * 2.2, dy * 2.2);
                    }
                    Role::Move { origin } => {
                        // Use absolute displacement from finger-down, so small motion samples do not flicker.
                        let dx = x - origin.0;
                        let dy = y - origin.1;
                        let threshold = 28.0;
                        key("KeyW", dy < -threshold);
                        key("KeyS", dy > threshold);
                        key("KeyA", dx < -threshold);
                        key("KeyD", dx > threshold);
                    }
                    _ => {}
                }
                f.last = (x, y);
            }
        }
        TouchPhase::Ended | TouchPhase::Cancelled => {
            if let Some(f) = fingers.remove(&t.id) {
                match f.role {
                    Role::Key(k) => key(k, false),
                    Role::Fire => input::button(0, false),
                    Role::Move { .. } => {
                        key("KeyW", false);
                        key("KeyS", false);
                        key("KeyA", false);
                        key("KeyD", false);
                    }
                    Role::Look => {}
                }
            }
        }
    }
}

pub fn release() {
    let mut fingers = FINGERS.lock().unwrap();
    for (_, f) in fingers.drain() {
        match f.role {
            Role::Key(k) => key(k, false),
            Role::Fire => input::button(0, false),
            Role::Move { .. } => {
                key("KeyW", false);
                key("KeyS", false);
                key("KeyA", false);
                key("KeyD", false);
            }
            Role::Look => {}
        }
    }
}
