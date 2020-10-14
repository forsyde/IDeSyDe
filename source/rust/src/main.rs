use clap::App;

fn main() {
    App::new("DeSyDe(R)")
       .version("0.1")
       .about("ForSyDe's Design Space Exploration Tool")
       .author("Rodolfo Jordao <jordao@kth.se>")
       .get_matches();
}
