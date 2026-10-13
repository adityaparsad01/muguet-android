fn main() {
    if let Err(error) = muguet::entry() {
        eprintln!("Muguet failed: {error:#}");
        std::process::exit(1);
    }
}
