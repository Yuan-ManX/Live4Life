+ Char {
	repeat { |n| ^if (n <= 1) { this } { this ++ this.repeat(n - 1) } }
}